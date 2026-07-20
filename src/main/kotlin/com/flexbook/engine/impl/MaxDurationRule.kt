package com.flexbook.engine.impl

import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import org.springframework.stereotype.Component
import java.time.temporal.ChronoUnit

/**
 * Caps the maximum length of a single booking.
 *
 * Required params:
 *   maxMinutes (Int) — maximum allowed booking duration
 *
 * Example config JSON: {"maxMinutes": 240}
 */
@Component
class MaxDurationRule : BookingRule {

    override val type = "MAX_DURATION"

    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val maxMinutes = config.params["maxMinutes"]?.let { (it as Number).toInt() }
            ?: return RuleResult.Failure("MAX_DURATION rule missing required param 'maxMinutes'")

        val durationMinutes = ChronoUnit.MINUTES.between(context.startTime, context.endTime)

        return if (durationMinutes <= maxMinutes) {
            RuleResult.Success
        } else {
            RuleResult.Failure(
                "Booking duration ${durationMinutes}min exceeds the maximum of ${maxMinutes}min " +
                "for '${context.resource.name}'"
            )
        }
    }
}
