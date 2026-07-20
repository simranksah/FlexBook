package com.flexbook.engine.impl

import com.flexbook.domain.BookingStatus
import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import org.springframework.stereotype.Component

/**
 * Forces a booking to CONFIRMED status (skipping PENDING) when all its conditions pass.
 *
 * Intended use: configure with a HIGHER priority than APPROVAL_REQUIRED.
 * The engine takes the first Transition result (highest priority), so when
 * AutoConfirm fires first and returns Transition(CONFIRMED), ApprovalRequired's
 * Transition(PENDING) is ignored.
 *
 * Optional params (all default to "always match"):
 *   maxGroupSize (Int)  — auto-confirm only if groupSize < this value
 *   startHour    (Int)  — auto-confirm only if booking starts at or after this hour
 *   endHour      (Int)  — auto-confirm only if booking starts before this hour
 *
 * Example config JSON: {"maxGroupSize": 5, "startHour": 9, "endHour": 17}
 */
@Component
class AutoConfirmRule : BookingRule {

    override val type = "AUTO_CONFIRM"

    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val maxGroupSize = config.params["maxGroupSize"]?.let { (it as Number).toInt() } ?: Int.MAX_VALUE
        val startHour    = config.params["startHour"]?.let { (it as Number).toInt() } ?: 0
        val endHour      = config.params["endHour"]?.let { (it as Number).toInt() } ?: 24

        val groupSize    = context.metadata["groupSize"]?.let { (it as Number).toInt() } ?: 0
        val bookingHour  = context.startTime.hour

        val conditionsMet = groupSize < maxGroupSize &&
                bookingHour >= startHour &&
                bookingHour < endHour

        return if (conditionsMet) {
            RuleResult.Transition(
                status = BookingStatus.CONFIRMED,
                reason = "Auto-confirmed: group=$groupSize < maxGroupSize=$maxGroupSize and within business hours"
            )
        } else {
            RuleResult.Success  // conditions didn't match — let other rules decide
        }
    }
}
