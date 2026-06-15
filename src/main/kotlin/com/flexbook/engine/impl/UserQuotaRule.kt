package com.flexbook.engine.impl

import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import com.flexbook.repository.BookingRepository
import org.springframework.stereotype.Component

/**
 * Limits how many bookings a single user can make within a rolling time period.
 *
 * Required params:
 *   maxBookings (Int) — maximum allowed bookings per period
 *   periodDays  (Int) — rolling window length in days
 *
 * Context requirements:
 *   BookingContext.userId must be non-null; if absent, rule passes with a warning.
 *
 * Example config JSON: {"maxBookings": 2, "periodDays": 7}
 */
@Component
class UserQuotaRule(
    private val bookingRepository: BookingRepository
) : BookingRule {

    override val type = "USER_QUOTA"

    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val maxBookings = config.params["maxBookings"]?.let { (it as Number).toInt() }
            ?: return RuleResult.Failure("USER_QUOTA rule missing required param 'maxBookings'")
        val periodDays = config.params["periodDays"]?.let { (it as Number).toLong() }
            ?: return RuleResult.Failure("USER_QUOTA rule missing required param 'periodDays'")

        val userId = context.userId
            ?: return RuleResult.Success  // no userId provided — rule cannot apply, skip

        val periodStart = context.startTime.minusDays(periodDays)
        val existingBookings = bookingRepository.countUserBookingsInPeriod(
            tenantId    = context.tenantId,
            userId      = userId,
            periodStart = periodStart
        )

        return if (existingBookings < maxBookings) {
            RuleResult.Success
        } else {
            RuleResult.Failure(
                "User '$userId' has reached the booking quota of $maxBookings " +
                "bookings per $periodDays days"
            )
        }
    }
}
