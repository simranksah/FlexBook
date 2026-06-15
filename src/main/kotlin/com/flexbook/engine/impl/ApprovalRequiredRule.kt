package com.flexbook.engine.impl

import com.flexbook.domain.BookingStatus
import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import org.springframework.stereotype.Component

/**
 * Routes large group bookings through an approval flow.
 *
 * Required params:
 *   threshold (Int) — group size at or above which approval is needed
 *
 * Context metadata:
 *   groupSize (Int) — number of attendees for this booking
 *
 * Example config JSON: {"threshold": 10}
 */
@Component
class ApprovalRequiredRule : BookingRule {

    override val type = "APPROVAL_REQUIRED"

    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val threshold = config.params["threshold"]?.let { (it as Number).toInt() }
            ?: return RuleResult.Failure("APPROVAL_REQUIRED rule missing required param 'threshold'")

        val groupSize = context.metadata["groupSize"]?.let { (it as Number).toInt() } ?: 0

        return if (groupSize >= threshold) {
            RuleResult.Transition(
                status = BookingStatus.PENDING,
                reason = "Group size $groupSize meets or exceeds threshold $threshold — manual approval required"
            )
        } else {
            RuleResult.Success
        }
    }
}
