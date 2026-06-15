package com.flexbook.engine.impl

import com.flexbook.engine.BookingContext
import com.flexbook.engine.BookingRule
import com.flexbook.engine.RuleConfig
import com.flexbook.engine.RuleResult
import com.flexbook.repository.BookingRepository
import org.springframework.stereotype.Component

/**
 * Enforces a minimum gap between consecutive bookings on the same resource.
 *
 * Required params:
 *   bufferMinutes (Int) — minimum gap in minutes
 *
 * Example config JSON: {"bufferMinutes": 15}
 *
 * A booking is rejected if any other active booking:
 *   - Ends within `bufferMinutes` before the requested start time, OR
 *   - Starts within `bufferMinutes` after the requested end time
 */
@Component
class BufferTimeRule(
    private val bookingRepository: BookingRepository
) : BookingRule {

    override val type = "BUFFER_TIME"

    override fun evaluate(context: BookingContext, config: RuleConfig): RuleResult {
        val bufferMinutes = config.params["bufferMinutes"]?.let { (it as Number).toLong() }
            ?: return RuleResult.Failure("BUFFER_TIME rule missing required param 'bufferMinutes'")

        val bufferStart = context.startTime.minusMinutes(bufferMinutes)
        val bufferEnd   = context.endTime.plusMinutes(bufferMinutes)

        val conflicts = bookingRepository.countBufferConflicts(
            resourceId  = context.resource.id,
            bufferStart = bufferStart,
            slotStart   = context.startTime,
            slotEnd     = context.endTime,
            bufferEnd   = bufferEnd
        )

        return if (conflicts == 0L) {
            RuleResult.Success
        } else {
            RuleResult.Failure(
                "'${context.resource.name}' requires a ${bufferMinutes}-minute gap between bookings. " +
                "The requested time slot conflicts with an adjacent booking."
            )
        }
    }
}
