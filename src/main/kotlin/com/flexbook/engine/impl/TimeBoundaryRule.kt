package com.flexbook.engine.impl

import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import org.springframework.stereotype.Component

/**
 * Constrains bookings to a daily time window.
 *
 * Required params:
 *   startHour (Int) — first allowed hour, inclusive (24h)
 *   endHour   (Int) — last allowed hour, inclusive (24h)
 *
 * Example config JSON: {"startHour": 8, "endHour": 18}
 */
@Component
class TimeBoundaryRule : BookingRule {

    override val type = "TIME_BOUNDARY"

    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val startHour = config.params["startHour"]?.let { (it as Number).toInt() }
            ?: return RuleResult.Failure("TIME_BOUNDARY rule missing required param 'startHour'")
        val endHour = config.params["endHour"]?.let { (it as Number).toInt() }
            ?: return RuleResult.Failure("TIME_BOUNDARY rule missing required param 'endHour'")

        val bookingStartHour = context.startTime.hour
        val bookingEndHour = context.endTime.hour
        val bookingEndMinute = context.endTime.minute

        // Allow end exactly on the boundary hour with zero minutes (e.g. ends at 18:00)
        val effectiveEndHour = if (bookingEndMinute == 0) bookingEndHour - 1 else bookingEndHour

        return if (bookingStartHour >= startHour && effectiveEndHour < endHour) {
            RuleResult.Success
        } else {
            RuleResult.Failure(
                "Bookings for '${context.resource.name}' must be within " +
                "%02d:00–%02d:00. Requested: %02d:00–%02d:%02d".format(
                    startHour, endHour,
                    context.startTime.hour, context.endTime.hour, context.endTime.minute
                )
            )
        }
    }
}
