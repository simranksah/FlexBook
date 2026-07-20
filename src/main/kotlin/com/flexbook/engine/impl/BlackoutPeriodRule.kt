package com.flexbook.engine.impl

import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Blocks all bookings during a configured date range.
 *
 * Required params:
 *   startDate (String, ISO date "YYYY-MM-DD") — first day of blackout, inclusive
 *   endDate   (String, ISO date "YYYY-MM-DD") — last day of blackout, inclusive
 *
 * Example config JSON: {"startDate": "2025-12-25", "endDate": "2026-01-03"}
 *
 * Semantics: a booking is blocked if its START date falls within [startDate, endDate].
 * This is a simplification; see DECISIONS.md for rationale.
 */
@Component
class BlackoutPeriodRule : BookingRule {

    override val type = "BLACKOUT_PERIOD"

    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val startDateStr = config.params["startDate"] as? String
            ?: return RuleResult.Failure("BLACKOUT_PERIOD rule missing required param 'startDate'")
        val endDateStr = config.params["endDate"] as? String
            ?: return RuleResult.Failure("BLACKOUT_PERIOD rule missing required param 'endDate'")

        val blackoutStart = runCatching { LocalDate.parse(startDateStr) }.getOrElse {
            return RuleResult.Failure("BLACKOUT_PERIOD: invalid startDate format '$startDateStr' (expected YYYY-MM-DD)")
        }
        val blackoutEnd = runCatching { LocalDate.parse(endDateStr) }.getOrElse {
            return RuleResult.Failure("BLACKOUT_PERIOD: invalid endDate format '$endDateStr' (expected YYYY-MM-DD)")
        }

        val bookingDate = context.startTime.toLocalDate()

        return if (!bookingDate.isBefore(blackoutStart) && !bookingDate.isAfter(blackoutEnd)) {
            RuleResult.Failure(
                "'${context.resource.name}' is unavailable during the blackout period " +
                "$blackoutStart to $blackoutEnd"
            )
        } else {
            RuleResult.Success
        }
    }
}
