package com.flexbook.domain

import jakarta.persistence.*
import org.hibernate.type.SqlTypes
import org.hibernate.annotations.JdbcTypeCode
import java.time.LocalDateTime
import java.util.UUID

/**
 * Stores a rule binding: which rule type applies, with what parameters,
 * and at what scope (tenant-wide, resource-type, or resource-specific).
 *
 * Scoping priority (highest wins):
 *   3. resourceId IS NOT NULL  → resource-specific rule
 *   2. resourceType IS NOT NULL → all resources of that type
 *   1. both NULL               → tenant-wide default
 */
@Entity
@Table(
    name = "rule_configurations",
    indexes = [
        Index(name = "idx_rule_cfg_tenant_id", columnList = "tenant_id"),
        Index(name = "idx_rule_cfg_resource_id", columnList = "resource_id")
    ]
)
data class RuleConfiguration(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    /** Null = applies to all resources (possibly filtered by resourceType). */
    @Column(name = "resource_id")
    val resourceId: UUID? = null,

    /** Null = applies to all resource types. */
    @Column(name = "resource_type")
    val resourceType: String? = null,

    /** Matches a registered BookingRule.type (e.g. "TIME_BOUNDARY"). */
    @Column(name = "rule_type", nullable = false)
    val ruleType: String,

    /**
     * Rule parameters stored as JSONB. Schema is rule-type specific:
     * TIME_BOUNDARY  → {"startHour": 8, "endHour": 18}
     * APPROVAL_REQUIRED → {"threshold": 10}
     * CAPACITY       → (no extra params; capacity comes from Resource entity)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    val params: String = "{}",

    /**
     * Evaluation order within a request. Higher = evaluated first.
     * Failure stops evaluation immediately.
     */
    @Column(nullable = false)
    val priority: Int = 0,

    @Column(nullable = false)
    val enabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
