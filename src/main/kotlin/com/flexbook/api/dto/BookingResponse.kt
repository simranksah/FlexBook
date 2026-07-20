package com.flexbook.api.dto

import com.flexbook.domain.Booking
import com.flexbook.domain.BookingStatus
import java.time.LocalDateTime
import java.util.UUID

data class BookingResponse(
    val id: UUID,
    val tenantId: UUID,
    val resourceId: UUID,
    val userId: String?,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val status: BookingStatus,
    val statusReason: String?,
    /** Base price in smallest currency unit (paise/cents). Null if resource is unpriced. */
    val basePrice: Long?,
    /** Compounded price multiplier from PeakPricing rules. 1.0 = no surcharge. */
    val priceMultiplier: Double,
    /** Final price = basePrice × priceMultiplier. Null if resource is unpriced. */
    val totalPrice: Long?,
    val createdAt: LocalDateTime
)

data class BatchBookingResult(
    val index: Int,
    val booking: BookingResponse? = null,
    val error: String? = null,
    /** Per-item HTTP status: 201 (created), 404 (not found), 422 (rule violation), 500 (error). */
    val httpStatus: Int
)

data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

fun Booking.toResponse() = BookingResponse(
    id = id,
    tenantId = tenantId,
    resourceId = resourceId,
    userId = userId,
    startTime = startTime,
    endTime = endTime,
    status = status,
    statusReason = statusReason,
    basePrice = basePrice,
    priceMultiplier = priceMultiplier,
    totalPrice = totalPrice,
    createdAt = createdAt
)
