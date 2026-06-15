package com.flexbook.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.flexbook.api.dto.BatchBookingRequest
import com.flexbook.api.dto.BatchBookingResult
import com.flexbook.api.dto.BookingRequest
import com.flexbook.api.dto.BookingResponse
import com.flexbook.api.dto.PagedResponse
import com.flexbook.api.dto.RecurrenceConfig
import com.flexbook.api.dto.RecurringBookingRequest
import com.flexbook.api.dto.toResponse
import com.flexbook.domain.Booking
import com.flexbook.domain.BookingStatus
import com.flexbook.domain.RuleConfiguration
import com.flexbook.engine.BookingContext
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleRegistry
import com.flexbook.engine.RuleResult
import com.flexbook.repository.BookingRepository
import com.flexbook.repository.BookingSpecification
import com.flexbook.repository.ResourceRepository
import com.flexbook.repository.RuleConfigurationRepository
import com.flexbook.repository.TenantRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class BookingService(
    private val tenantRepository: TenantRepository,
    private val resourceRepository: ResourceRepository,
    private val bookingRepository: BookingRepository,
    private val ruleConfigRepository: RuleConfigurationRepository,
    private val ruleRegistry: RuleRegistry,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun createBooking(tenantId: UUID, request: BookingRequest): BookingResponse {
        require(request.endTime.isAfter(request.startTime)) {
            "endTime must be after startTime"
        }

        val tenant = tenantRepository.findByIdAndEnabledTrue(tenantId)
            ?: throw NotFoundException("Tenant $tenantId not found or disabled")

        val resource = resourceRepository.findByIdAndTenantIdAndEnabledTrue(request.resourceId, tenantId)
            ?: throw NotFoundException("Resource ${request.resourceId} not found for tenant $tenantId")

        val requestMetadata = request.metadata ?: emptyMap()
        val currentBookings = bookingRepository.countOverlapping(
            resourceId = resource.id,
            startTime  = request.startTime,
            endTime    = request.endTime
        )

        val context = BookingContext(
            tenantId  = tenantId,
            resource  = resource,
            userId    = request.userId,
            startTime = request.startTime,
            endTime   = request.endTime,
            metadata  = requestMetadata + mapOf("currentBookings" to currentBookings.toInt())
        )

        val applicableRules = ruleConfigRepository.findAllApplicable(
            tenantId     = tenantId,
            resourceId   = resource.id,
            resourceType = resource.type
        )

        val evalResult = evaluateRules(context, applicableRules)

        val (basePrice, totalPrice) = computePrice(
            basePricePerHour = resource.basePricePerHour,
            startTime        = request.startTime,
            endTime          = request.endTime,
            multiplier       = evalResult.priceMultiplier
        )

        val booking = Booking(
            tenantId       = tenantId,
            resourceId     = resource.id,
            userId         = request.userId,
            startTime      = request.startTime,
            endTime        = request.endTime,
            status         = evalResult.status,
            statusReason   = evalResult.statusReason,
            metadata       = objectMapper.writeValueAsString(requestMetadata),
            basePrice      = basePrice,
            priceMultiplier = evalResult.priceMultiplier,
            totalPrice     = totalPrice
        )

        return bookingRepository.save(booking).toResponse()
    }

    @Transactional(readOnly = true)
    fun getBooking(tenantId: UUID, bookingId: UUID): BookingResponse =
        bookingRepository.findByIdAndTenantId(bookingId, tenantId)?.toResponse()
            ?: throw NotFoundException("Booking $bookingId not found for tenant $tenantId")

    @Transactional
    fun cancelBooking(tenantId: UUID, bookingId: UUID): BookingResponse {
        val booking = bookingRepository.findByIdAndTenantId(bookingId, tenantId)
            ?: throw NotFoundException("Booking $bookingId not found for tenant $tenantId")

        if (booking.status == BookingStatus.CANCELLED) {
            throw ValidationException("Booking $bookingId is already cancelled")
        }

        booking.status = BookingStatus.CANCELLED
        booking.statusReason = "Cancelled by tenant"
        return bookingRepository.save(booking).toResponse()
    }

    @Transactional(readOnly = true)
    fun queryBookings(
        tenantId: UUID,
        resourceId: UUID? = null,
        userId: String? = null,
        status: BookingStatus? = null,
        from: LocalDateTime? = null,
        to: LocalDateTime? = null,
        page: Int = 0,
        size: Int = 20
    ): PagedResponse<BookingResponse> {
        val spec = BookingSpecification.build(tenantId, resourceId, userId, status, from, to)
        val pageResult = bookingRepository.findAll(
            spec, PageRequest.of(page, size, Sort.by("createdAt").descending())
        )
        return PagedResponse(
            content       = pageResult.content.map { it.toResponse() },
            page          = page,
            size          = size,
            totalElements = pageResult.totalElements,
            totalPages    = pageResult.totalPages
        )
    }

    /**
     * Validates and creates a batch of bookings independently.
     * One failure does NOT block other items. Returns 207 semantics.
     */
    @Transactional
    fun createBatchBookings(tenantId: UUID, batch: BatchBookingRequest): List<BatchBookingResult> =
        batch.bookings.mapIndexed { index, request ->
            runCatching { createBooking(tenantId, request) }
                .fold(
                    onSuccess = { BatchBookingResult(index = index, booking = it, httpStatus = 201) },
                    onFailure = { ex ->
                        val (httpStatus, message) = when (ex) {
                            is ValidationException  -> 422 to ex.message
                            is NotFoundException    -> 404 to ex.message
                            is IllegalArgumentException -> 400 to ex.message
                            else                   -> 500 to "Unexpected error: ${ex.message}"
                        }
                        BatchBookingResult(index = index, error = message, httpStatus = httpStatus)
                    }
                )
        }

    /**
     * Creates a series of recurring bookings from a single request.
     * Generates [RecurrenceConfig.weekCount] individual bookings at the same
     * time slot on consecutive weeks. Each instance is validated independently
     * against all applicable rules (partial success).
     */
    @Transactional
    fun createRecurringBookings(
        tenantId: UUID,
        request: RecurringBookingRequest
    ): List<BatchBookingResult> {
        val base = request.booking
        val rec  = request.recurrence

        val dayOfWeek = DayOfWeek.valueOf(rec.dayOfWeek.uppercase())
        val startDate = rec.startDate?.let { LocalDate.parse(it) }
            ?: base.startTime.toLocalDate()

        // Align to the specified day of week
        val daysUntilTarget = (dayOfWeek.value - startDate.dayOfWeek.value + 7) % 7
        val firstDate = startDate.plusDays(daysUntilTarget.toLong())

        val instances = (0 until rec.weekCount).map { weekOffset ->
            val occurrenceDate = firstDate.plusWeeks(weekOffset.toLong())
            val startTime = base.startTime.with(occurrenceDate)
            val endTime   = base.endTime.with(occurrenceDate)
            base.copy(startTime = startTime, endTime = endTime)
        }

        return createBatchBookings(tenantId, BatchBookingRequest(instances))
    }

    private data class EvaluationResult(
        val status: BookingStatus,
        val statusReason: String?,
        val priceMultiplier: Double
    )

    /**
     * Evaluates all rules in priority order (DESC).
     *
     * - Failure short-circuits immediately — booking rejected.
     * - Transition: the FIRST one seen (highest-priority rule) wins.
     *   This lets AUTO_CONFIRM override APPROVAL_REQUIRED by having higher priority.
     * - PriceModifier: all are compounded (multiplied together).
     * - Success: no-op, continue.
     */
    private fun evaluateRules(
        context: BookingContext,
        configs: List<RuleConfiguration>
    ): EvaluationResult {
        var finalStatus: BookingStatus? = null
        var statusReason: String? = null
        var priceMultiplier = 1.0

        for (config in configs) {
            val rule       = ruleRegistry.get(config.ruleType)
            val ruleConfig = config.toRuleConfig()

            when (val result = rule.evaluate(context, ruleConfig)) {
                is RuleResult.Failure      -> throw ValidationException(result.message)
                is RuleResult.Transition   -> if (finalStatus == null) {
                    finalStatus  = result.status   // first (highest-priority) transition wins
                    statusReason = result.reason
                }
                is RuleResult.PriceModifier -> priceMultiplier *= result.multiplier
                is RuleResult.Success       -> Unit
            }
        }

        return EvaluationResult(
            status         = finalStatus ?: BookingStatus.CONFIRMED,
            statusReason   = statusReason,
            priceMultiplier = priceMultiplier
        )
    }

    /**
     * Computes basePrice and totalPrice in the smallest currency unit.
     * Uses exact minute-based duration to avoid rounding on short bookings.
     * Returns (null, null) when the resource has no base price configured.
     */
    private fun computePrice(
        basePricePerHour: Long?,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        multiplier: Double
    ): Pair<Long?, Long?> {
        if (basePricePerHour == null) return null to null
        val durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime)
        val basePrice       = (basePricePerHour.toDouble() * durationMinutes / 60).toLong()
        val totalPrice      = (basePrice * multiplier).toLong()
        return basePrice to totalPrice
    }

    private fun RuleConfiguration.toRuleConfig(): RuleConfig = RuleConfig(
        ruleType = ruleType,
        params   = objectMapper.readValue(params, object : TypeReference<Map<String, Any>>() {}),
        priority = priority
    )
}

// ─── Domain exceptions ────────────────────────────────────────────────────────

class ValidationException(message: String?) : RuntimeException(message)
class NotFoundException(message: String?) : RuntimeException(message)
