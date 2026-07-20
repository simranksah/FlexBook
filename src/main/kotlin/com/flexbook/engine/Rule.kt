package com.flexbook.engine

import com.flexbook.domain.BookingStatus
import com.flexbook.domain.Resource
import java.time.LocalDateTime
import java.util.UUID

// ─── Result types ──────────────────────────────────────────────────────────────

sealed class RuleResult {
    /** Rule passed — continue evaluating remaining rules. */
    object Success : RuleResult()

    /** Rule failed — reject the booking immediately. Evaluation stops. */
    data class Failure(val message: String) : RuleResult()

    /**
     * Rule requires a status transition (e.g. PENDING for approval, CONFIRMED for auto-confirm).
     * The first Transition seen (highest-priority rule) wins when multiple rules conflict.
     * This enables AutoConfirmRule to override ApprovalRequiredRule by having higher priority.
     */
    data class Transition(val status: BookingStatus, val reason: String) : RuleResult()

    /**
     * Rule applies a price multiplier to the booking cost.
     * All PriceModifier results are compounded (multiplied together).
     * E.g., 2x evening peak + 1.5x weekend = 3x total.
     */
    data class PriceModifier(val multiplier: Double, val reason: String) : RuleResult()
}

// ─── Context ───────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of everything a rule needs.
 * Extra domain data (group size, current occupancy, etc.) goes in [metadata].
 */
data class BookingContext(
    val tenantId: UUID,
    val resource: Resource,
    /** Tenant-provided user identifier. Null if not supplied in the request. */
    val userId: String?,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val metadata: Map<String, Any> = emptyMap()
)

// ─── Config ────────────────────────────────────────────────────────────────────

/**
 * Parsed rule configuration passed to each [BookingRule.evaluate] call.
 */
data class RuleConfig(
    val ruleType: String,
    val params: Map<String, Any>,
    val priority: Int
)

// ─── Contract ──────────────────────────────────────────────────────────────────

/**
 * Extensibility contract: add a new rule type by:
 *   1. Creating a class that implements this interface
 *   2. Annotating it @Component
 *   3. Inserting a rule_configurations row with the matching rule_type
 *
 * No other wiring required — RuleRegistry auto-discovers all beans.
 */
interface BookingRule {
    /** Unique identifier matched against RuleConfiguration.ruleType. */
    val type: String

    fun evaluate(context: BookingContext, config: RuleConfig): RuleResult
}
