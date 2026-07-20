package com.flexbook.engine.impl

import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import org.springframework.stereotype.Component
import java.time.DayOfWeek

/**
 * Validates recurring booking configurations. This rule does not block or
 * transition — it acts as a constraint guard on recurrence metadata.
 *
 * When a booking request includes recurrence metadata:
 *   dayOfWeek (String) — must match the booking's start day (e.g. "MONDAY")
 *   weekCount (Int)    — number of weekly occurrences (max 12)
 *
 * If the recurrence config is valid, the rule passes. The service layer
 * then generates individual booking instances from the recurrence pattern.
 *
 * Example config JSON: {"maxWeeks": 12}
 */
@Component
class RecurringSlotRule : BookingRule {

    override val type = "RECURRING_SLOT"

    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val maxWeeks = config.params["maxWeeks"]?.let { (it as Number).toInt() } ?: 12

        val recurrenceMeta = context.metadata["recurrence"] as? Map<*, *>
            ?: return RuleResult.Success  // not a recurring booking — nothing to validate

        val dayOfWeekStr = recurrenceMeta["dayOfWeek"] as? String
            ?: return RuleResult.Failure("RECURRING_SLOT: missing 'dayOfWeek' in recurrence config")
        val weekCount = recurrenceMeta["weekCount"]?.let { (it as Number).toInt() }
            ?: return RuleResult.Failure("RECURRING_SLOT: missing 'weekCount' in recurrence config")

        val requestedDay = runCatching { DayOfWeek.valueOf(dayOfWeekStr.uppercase()) }.getOrElse {
            return RuleResult.Failure(
                "RECURRING_SLOT: invalid dayOfWeek '$dayOfWeekStr'. " +
                "Expected one of: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY"
            )
        }

        if (context.startTime.dayOfWeek != requestedDay) {
            return RuleResult.Failure(
                "RECURRING_SLOT: booking start time is ${context.startTime.dayOfWeek}, " +
                "but recurrence is configured for $requestedDay"
            )
        }

        if (weekCount < 1) {
            return RuleResult.Failure("RECURRING_SLOT: weekCount must be at least 1")
        }

        if (weekCount > maxWeeks) {
            return RuleResult.Failure(
                "RECURRING_SLOT: weekCount $weekCount exceeds maximum of $maxWeeks weeks"
            )
        }

        return RuleResult.Success
    }
}
