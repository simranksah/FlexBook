package com.flexbook.engine.impl

import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import org.springframework.stereotype.Component

/**
 * Prevents overbooking by comparing current occupancy against the resource's capacity.
 *
 * No extra params required — capacity is read from [BookingContext.resource.capacity].
 *
 * Context metadata (pre-populated by BookingService):
 *   currentBookings (Int) — number of active overlapping bookings for this resource/slot
 *
 * Example config JSON: {}
 */
@Component
class CapacityRule : BookingRule {

    override val type = "CAPACITY"

    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val capacity = context.resource.capacity
        val currentBookings = context.metadata["currentBookings"]?.let { (it as Number).toInt() } ?: 0

        return if (currentBookings < capacity) {
            RuleResult.Success
        } else {
            RuleResult.Failure(
                "'${context.resource.name}' is fully booked for the requested time slot " +
                "(capacity: $capacity, current: $currentBookings)"
            )
        }
    }
}
