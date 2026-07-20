package com.flexbook.api.dto

import com.flexbook.domain.RuleConfiguration
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime
import java.util.UUID

data class CreateRuleRequest(
    /** Must match a registered BookingRule.type (e.g. "TIME_BOUNDARY", "CAPACITY"). */
    @field:NotBlank val ruleType: String,

    /**
     * Rule-specific parameters. Schema varies by rule type:
     *   TIME_BOUNDARY      → {"startHour": 8, "endHour": 18}
     *   MAX_DURATION       → {"maxMinutes": 240}
     *   BUFFER_TIME        → {"bufferMinutes": 15}
     *   CAPACITY           → {}
     *   BLACKOUT_PERIOD    → {"startDate": "2025-12-25", "endDate": "2026-01-03"}
     *   APPROVAL_REQUIRED  → {"threshold": 10}
     *   USER_QUOTA         → {"maxBookings": 2, "periodDays": 7}
     *   AUTO_CONFIRM       → {"maxGroupSize": 5, "startHour": 9, "endHour": 17}
     *   PEAK_PRICING       → {"multiplier": 2.0, "startHour": 17, "endHour": 21}
     *   COOLDOWN           → {"cooldownHours": 24}
     *   DEPENDENT_RESOURCE → {"requiredResourceId": "0000..."}
     *   RECURRING_SLOT     → {"maxWeeks": 12}
     */
    val params: Map<String, Any> = emptyMap(),

    /**
     * Evaluation order. Higher = evaluated first.
     * Use priority to resolve conflicts: e.g. AUTO_CONFIRM at 100 overrides
     * APPROVAL_REQUIRED at 50 when their Transition results conflict.
     */
    val priority: Int = 0,

    /** Null = applies to all resources (scoped further by resourceType if set). */
    val resourceId: UUID? = null,

    /** Null = applies to all resource types. */
    val resourceType: String? = null
)

data class RuleResponse(
    val id: UUID,
    val tenantId: UUID,
    val ruleType: String,
    val params: Map<String, Any>,
    val priority: Int,
    val resourceId: UUID?,
    val resourceType: String?,
    val enabled: Boolean,
    val createdAt: LocalDateTime
)
