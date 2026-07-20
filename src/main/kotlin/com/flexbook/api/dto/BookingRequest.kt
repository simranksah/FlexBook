package com.flexbook.api.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import java.util.UUID

data class BookingRequest(
    @field:NotNull val resourceId: UUID,
    @field:NotNull val startTime: LocalDateTime,
    @field:NotNull val endTime: LocalDateTime,

    /**
     * Tenant-provided user identifier. Required for UserQuota and Cooldown rules.
     * FlexBook has no built-in auth — tenants supply their own user IDs.
     */
    val userId: String? = null,

    /** Arbitrary key-value metadata forwarded to rule evaluation (e.g. groupSize). */
    val metadata: Map<String, Any>? = null
)

data class BatchBookingRequest(
    @field:NotNull val bookings: List<BookingRequest>
)

/**
 * A single booking request with a recurrence pattern.
 * The service generates [RecurrenceConfig.weekCount] individual bookings,
 * each validated independently against all applicable rules.
 */
data class RecurringBookingRequest(
    @field:NotNull val booking: BookingRequest,
    @field:NotNull val recurrence: RecurrenceConfig
)

data class RecurrenceConfig(
    /** Day of week for recurrence, e.g. "MONDAY". Must match booking startTime's day. */
    val dayOfWeek: String,
    /** Number of weekly occurrences (including the current week). Max 12. */
    val weekCount: Int,
    /** Optional ISO start date override. Defaults to booking.startTime's date. */
    val startDate: String? = null
)
