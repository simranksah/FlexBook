package com.flexbook.api

import com.flexbook.api.dto.BatchBookingRequest
import com.flexbook.api.dto.BatchBookingResult
import com.flexbook.api.dto.BookingRequest
import com.flexbook.api.dto.BookingResponse
import com.flexbook.api.dto.PagedResponse
import com.flexbook.api.dto.RecurringBookingRequest
import com.flexbook.domain.BookingStatus
import com.flexbook.service.BookingService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.UUID

@RestController
@RequestMapping("/tenants/{tenantId}/bookings")
class BookingController(private val bookingService: BookingService) {

    /**
     * POST /tenants/{tenantId}/bookings
     * Create a single booking. Evaluated against all applicable rules.
     * Returns 201 with CONFIRMED or PENDING status depending on rule outcomes.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBooking(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: BookingRequest
    ): BookingResponse = bookingService.createBooking(tenantId, request)

    /**
     * POST /tenants/{tenantId}/bookings/batch
     * Validate and create multiple bookings independently.
     * Always returns 207 Multi-Status; inspect each item's httpStatus field.
     */
    @PostMapping("/batch")
    fun createBatch(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody batch: BatchBookingRequest
    ): ResponseEntity<List<BatchBookingResult>> {
        val results = bookingService.createBatchBookings(tenantId, batch)
        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(results)
    }

    /**
     * GET /tenants/{tenantId}/bookings
     * Query bookings with optional filters.
     * Supports pagination (page/size) and filtering by resourceId, userId, status, time range.
     */
    @GetMapping
    fun queryBookings(
        @PathVariable tenantId: UUID,
        @RequestParam(required = false) resourceId: UUID?,
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) status: BookingStatus?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PagedResponse<BookingResponse> = bookingService.queryBookings(
        tenantId = tenantId, resourceId = resourceId, userId = userId,
        status = status, from = from, to = to, page = page, size = size
    )

    /** GET /tenants/{tenantId}/bookings/{id} — Fetch a single booking. */
    @GetMapping("/{id}")
    fun getBooking(
        @PathVariable tenantId: UUID,
        @PathVariable id: UUID
    ): BookingResponse = bookingService.getBooking(tenantId, id)

    /**
     * POST /tenants/{tenantId}/bookings/recurring
     * Create a series of recurring bookings from a single request.
     * Each occurrence is validated independently — partial success is expected.
     * Returns 207 Multi-Status with per-item results.
     */
    @PostMapping("/recurring")
    fun createRecurring(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: RecurringBookingRequest
    ): ResponseEntity<List<BatchBookingResult>> {
        val results = bookingService.createRecurringBookings(tenantId, request)
        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(results)
    }

    /** DELETE /tenants/{tenantId}/bookings/{id} — Cancel a booking. */
    @DeleteMapping("/{id}")
    fun cancelBooking(
        @PathVariable tenantId: UUID,
        @PathVariable id: UUID
    ): BookingResponse = bookingService.cancelBooking(tenantId, id)
}
