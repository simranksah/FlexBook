package com.flexbook.repository

import com.flexbook.domain.Booking
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface BookingRepository : JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    fun findByIdAndTenantId(id: UUID, tenantId: UUID): Booking?

    /**
     * Counts active overlapping bookings for a resource in a given time slot.
     * Used by CapacityRule to pre-populate context.metadata["currentBookings"].
     */
    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.resourceId = :resourceId
          AND b.status NOT IN ('CANCELLED', 'REJECTED')
          AND b.startTime < :endTime
          AND b.endTime > :startTime
    """)
    fun countOverlapping(
        @Param("resourceId") resourceId: UUID,
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): Long

    /**
     * Detects buffer zone violations: any active booking that ends within
     * [bufferStart, slotStart) or starts within [slotEnd, bufferEnd).
     *
     * Used by BufferTimeRule (injected directly into the rule implementation).
     *
     * bufferStart = slotStart - bufferMinutes
     * bufferEnd   = slotEnd   + bufferMinutes
     */
    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.resourceId = :resourceId
          AND b.status NOT IN ('CANCELLED', 'REJECTED')
          AND (
            (b.endTime > :bufferStart AND b.endTime <= :slotStart)
            OR
            (b.startTime >= :slotEnd AND b.startTime < :bufferEnd)
          )
    """)
    fun countBufferConflicts(
        @Param("resourceId") resourceId: UUID,
        @Param("bufferStart") bufferStart: LocalDateTime,
        @Param("slotStart") slotStart: LocalDateTime,
        @Param("slotEnd") slotEnd: LocalDateTime,
        @Param("bufferEnd") bufferEnd: LocalDateTime
    ): Long

    /**
     * Counts a user's non-cancelled bookings within a tenant from a given date.
     * Used by UserQuotaRule.
     */
    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.tenantId = :tenantId
          AND b.userId = :userId
          AND b.status NOT IN ('CANCELLED', 'REJECTED')
          AND b.startTime >= :periodStart
    """)
    fun countUserBookingsInPeriod(
        @Param("tenantId") tenantId: UUID,
        @Param("userId") userId: String,
        @Param("periodStart") periodStart: LocalDateTime
    ): Long

    /**
     * Checks for cooldown violations: same user, same resource, previous booking
     * ended within cooldownHours before the requested start time.
     * Used by CooldownRule.
     */
    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.resourceId = :resourceId
          AND b.userId = :userId
          AND b.status NOT IN ('CANCELLED', 'REJECTED')
          AND b.endTime > :cooldownStart
          AND b.endTime <= :bookingStart
    """)
    fun countCooldownConflicts(
        @Param("resourceId") resourceId: UUID,
        @Param("userId") userId: String,
        @Param("bookingStart") bookingStart: LocalDateTime,
        @Param("cooldownStart") cooldownStart: LocalDateTime
    ): Long

    /**
     * Counts a user's non-cancelled bookings for a specific resource in a time window.
     * Used by DependentResourceRule to check if the required resource is booked.
     */
    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.resourceId = :resourceId
          AND b.userId = :userId
          AND b.status NOT IN ('CANCELLED', 'REJECTED')
          AND b.startTime < :endTime
          AND b.endTime > :startTime
    """)
    fun countUserOverlappingForResource(
        @Param("resourceId") resourceId: UUID,
        @Param("userId") userId: String,
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): Long
}
