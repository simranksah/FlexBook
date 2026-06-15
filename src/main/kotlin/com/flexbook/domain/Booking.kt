package com.flexbook.domain

import jakarta.persistence.*
import org.hibernate.type.SqlTypes
import org.hibernate.annotations.JdbcTypeCode
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "bookings",
    indexes = [
        Index(name = "idx_bookings_tenant_id", columnList = "tenant_id"),
        Index(name = "idx_bookings_resource_id", columnList = "resource_id"),
        Index(name = "idx_bookings_status", columnList = "status"),
        Index(name = "idx_bookings_user_id", columnList = "tenant_id, user_id"),
        Index(name = "idx_bookings_time_range", columnList = "resource_id, start_time, end_time")
    ]
)
data class Booking(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "resource_id", nullable = false)
    val resourceId: UUID,

    /**
     * Tenant-provided user identifier. Used for UserQuota and Cooldown rules.
     * No auth system — tenants supply their own user identifiers.
     */
    @Column(name = "user_id")
    val userId: String? = null,

    @Column(name = "start_time", nullable = false)
    val startTime: LocalDateTime,

    @Column(name = "end_time", nullable = false)
    val endTime: LocalDateTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BookingStatus = BookingStatus.CONFIRMED,

    @Column(name = "status_reason")
    var statusReason: String? = null,

    /**
     * Arbitrary booking metadata stored as JSONB (groupSize, contact info, etc.).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    val metadata: String = "{}",

    /**
     * Base price in smallest currency unit (paise, cents) computed from
     * resource.basePricePerHour × duration. Null if resource has no pricing.
     */
    @Column(name = "base_price")
    val basePrice: Long? = null,

    /**
     * Compounded multiplier from all applicable PriceModifier rules (1.0 = no surcharge).
     */
    @Column(name = "price_multiplier", nullable = false)
    val priceMultiplier: Double = 1.0,

    /**
     * Final price = basePrice × priceMultiplier. Null if resource has no pricing.
     */
    @Column(name = "total_price")
    val totalPrice: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
