package com.flexbook.engine.impl

import com.flexbook.domain.BookingStatus
import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import com.flexbook.repository.BookingRepository
import org.springframework.stereotype.Component

/**
 * Requires that a dependent/secondary resource is also booked by the same user
 * in an overlapping time window. If the required resource is missing, the
 * booking is routed to PENDING for manual review.
 *
 * Required params:
 *   requiredResourceId (UUID) — the resource that must also be booked
 *
 * Context requirements:
 *   BookingContext.userId must be non-null; if absent, rule passes (cannot verify).
 *
 * Example config JSON: {"requiredResourceId": "00000000-0000-0000-0001-000000000003"}
 */
@Component
class DependentResourceRule(
    private val bookingRepository: BookingRepository
) : BookingRule {

    override val type = "DEPENDENT_RESOURCE"

    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val requiredResourceIdStr = config.params["requiredResourceId"] as? String
            ?: return RuleResult.Failure(
                "DEPENDENT_RESOURCE rule missing required param 'requiredResourceId'"
            )

        val requiredResourceId = runCatching { java.util.UUID.fromString(requiredResourceIdStr) }.getOrElse {
            return RuleResult.Failure(
                "DEPENDENT_RESOURCE: invalid requiredResourceId format '$requiredResourceIdStr'"
            )
        }

        val userId = context.userId
            ?: return RuleResult.Success  // no userId — cannot verify dependency

        val dependentCount = bookingRepository.countUserOverlappingForResource(
            resourceId = requiredResourceId,
            userId     = userId,
            startTime  = context.startTime,
            endTime    = context.endTime
        )

        return if (dependentCount > 0) {
            RuleResult.Success
        } else {
            RuleResult.Transition(
                status = BookingStatus.PENDING,
                reason = "This resource requires '$requiredResourceId' to also be booked " +
                         "by user '$userId' in the same time window"
            )
        }
    }
}
