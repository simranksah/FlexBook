package com.flexbook.domain

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "resources",
    indexes = [
        Index(name = "idx_resources_tenant_id", columnList = "tenant_id"),
        Index(name = "idx_resources_type", columnList = "type")
    ]
)
data class Resource(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(nullable = false)
    val name: String,

    /**
     * Logical type used for rule scoping (e.g. "ROOM", "DESK", "VEHICLE").
     * Case-sensitive; tenants define their own taxonomy.
     */
    @Column(nullable = false)
    val type: String,

    /**
     * Maximum concurrent bookings allowed for this resource.
     */
    @Column(nullable = false)
    val capacity: Int = 1,

    /**
     * Base price per hour in the smallest currency unit (paise, cents, etc.).
     * Null means this resource has no pricing — PeakPricingRule will be a no-op.
     */
    @Column(name = "base_price_per_hour")
    val basePricePerHour: Long? = null,

    @Column(nullable = false)
    val enabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
