package com.flexbook.engine.impl

import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import com.flexbook.repository.BookingRepository
import org.springframework.stereotype.Component

/**
 * Prevents a user from rebooking the same resource within a cooldown period
 * after their previous booking ended.
 *
 * Required params:
 *   cooldownHours (Int) — minimum hours between consecutive bookings by same user
 *
 * Context requirements:
 *   BookingContext.userId must be non-null; if absent, rule passes (cannot verify).
 *
 * Example config JSON: {"cooldownHours": 24}
 */
@Component
class CooldownRule(
    private val bookingRepository: BookingRepository
) : BookingRule {

    override val type = "COOLDOWN"

    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val cooldownHours = config.params["cooldownHours"]?.let { (it as Number).toLong() }
            ?: return RuleResult.Failure("COOLDOWN rule missing required param 'cooldownHours'")

        val userId = context.userId
            ?: return RuleResult.Success  // no userId — rule cannot apply

        val cooldownStart = context.startTime.minusHours(cooldownHours)
        val conflicts = bookingRepository.countCooldownConflicts(
            resourceId    = context.resource.id,
            userId        = userId,
            bookingStart  = context.startTime,
            cooldownStart = cooldownStart
        )

        return if (conflicts == 0L) {
            RuleResult.Success
        } else {
            RuleResult.Failure(
                "User '$userId' cannot rebook '${context.resource.name}' within $cooldownHours hours " +
                "of a previous booking (cooldown active)"
            )
        }
    }
}
