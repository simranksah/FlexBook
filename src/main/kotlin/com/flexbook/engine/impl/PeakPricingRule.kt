package com.flexbook.engine.impl

import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import org.springframework.stereotype.Component
import java.time.DayOfWeek

/**
 * Applies a price multiplier during configured peak windows.
 * Resources must have basePricePerHour set, otherwise this rule has no visible effect
 * (the multiplier is stored but totalPrice remains null).
 *
 * Required params:
 *   multiplier (Double) — price multiplier (e.g. 2.0 = 2× base price)
 *   startHour  (Int)    — peak window start hour, inclusive (24h)
 *   endHour    (Int)    — peak window end hour, exclusive (24h)
 *
 * Optional params:
 *   daysOfWeek (List<String>) — restrict to specific days e.g. ["MONDAY","TUESDAY"].
 *                               Absent = applies every day.
 *
 * Example config JSON: {"multiplier": 2.0, "startHour": 17, "endHour": 21}
 *
 * Simplification (documented in DECISIONS.md): applies based on booking START time only.
 * Bookings spanning the boundary are not prorated.
 */
@Component
class PeakPricingRule : BookingRule {

    override val type = "PEAK_PRICING"

    @Suppress("UNCHECKED_CAST")
    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val multiplier = config.params["multiplier"]?.let { (it as Number).toDouble() }
            ?: return RuleResult.Failure("PEAK_PRICING rule missing required param 'multiplier'")
        val peakStart = config.params["startHour"]?.let { (it as Number).toInt() }
            ?: return RuleResult.Failure("PEAK_PRICING rule missing required param 'startHour'")
        val peakEnd = config.params["endHour"]?.let { (it as Number).toInt() }
            ?: return RuleResult.Failure("PEAK_PRICING rule missing required param 'endHour'")

        val allowedDays = (config.params["daysOfWeek"] as? List<*>)
            ?.mapNotNull { runCatching { DayOfWeek.valueOf(it.toString()) }.getOrNull() }

        val bookingDay  = context.startTime.dayOfWeek
        val bookingHour = context.startTime.hour

        val dayMatches  = allowedDays == null || bookingDay in allowedDays
        val timeMatches = bookingHour >= peakStart && bookingHour < peakEnd

        return if (dayMatches && timeMatches) {
            RuleResult.PriceModifier(
                multiplier = multiplier,
                reason = "Peak pricing ${peakStart}h–${peakEnd}h on $bookingDay: ${multiplier}× surcharge"
            )
        } else {
            RuleResult.Success
        }
    }
}
