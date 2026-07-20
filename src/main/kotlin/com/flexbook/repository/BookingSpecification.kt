package com.flexbook.repository

import com.flexbook.domain.Booking
import com.flexbook.domain.BookingStatus
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDateTime
import java.util.UUID

/**
 * Factory methods for composing JPA Specification predicates for the booking query endpoint.
 * Each method returns null when the filter value is absent so callers can safely use
 * Kotlin's ?.let chaining with Specification.and().
 */
object BookingSpecification {

    fun withTenantId(tenantId: UUID): Specification<Booking> =
        Specification { root, _, cb -> cb.equal(root.get<UUID>("tenantId"), tenantId) }

    fun withResourceId(resourceId: UUID?): Specification<Booking>? = resourceId?.let {
        Specification { root, _, cb -> cb.equal(root.get<UUID>("resourceId"), it) }
    }

    fun withUserId(userId: String?): Specification<Booking>? = userId?.let {
        Specification { root, _, cb -> cb.equal(root.get<String>("userId"), it) }
    }

    fun withStatus(status: BookingStatus?): Specification<Booking>? = status?.let {
        Specification { root, _, cb -> cb.equal(root.get<BookingStatus>("status"), it) }
    }

    fun withStartFrom(from: LocalDateTime?): Specification<Booking>? = from?.let {
        Specification { root, _, cb -> cb.greaterThanOrEqualTo(root.get("startTime"), it) }
    }

    fun withStartTo(to: LocalDateTime?): Specification<Booking>? = to?.let {
        Specification { root, _, cb -> cb.lessThanOrEqualTo(root.get("startTime"), it) }
    }

    /**
     * Combines all optional filters with the mandatory tenantId filter.
     * Optional filters are ignored (not added to the query) when their value is null.
     */
    fun build(
        tenantId: UUID,
        resourceId: UUID? = null,
        userId: String? = null,
        status: BookingStatus? = null,
        from: LocalDateTime? = null,
        to: LocalDateTime? = null
    ): Specification<Booking> = withTenantId(tenantId)
        .and(withResourceId(resourceId))
        .and(withUserId(userId))
        .and(withStatus(status))
        .and(withStartFrom(from))
        .and(withStartTo(to))
}
