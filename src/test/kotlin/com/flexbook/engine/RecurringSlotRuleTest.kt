package com.flexbook.engine

import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.Failure
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.impl.RecurringSlotRule
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RecurringSlotRuleTest {

    private val rule = RecurringSlotRule()
    private val resource = Resource(tenantId = UUID.randomUUID(), name = "Room A", type = "ROOM")
    private val config = RuleConfig("RECURRING_SLOT", mapOf("maxWeeks" to 12), priority = 10)

    private fun context(dayOfWeek: String = "MONDAY", weekCount: Int = 4, hour: Int = 17) = BookingContext(
        tenantId  = UUID.randomUUID(),
        resource  = resource,
        userId    = "user-123",
        startTime = LocalDateTime.of(2025, 1, 6, hour, 0),  // Jan 6, 2025 was a Monday
        endTime   = LocalDateTime.of(2025, 1, 6, hour + 1, 0),
        metadata  = mapOf(
            "recurrence" to mapOf(
                "dayOfWeek" to dayOfWeek,
                "weekCount" to weekCount
            )
        )
    )

    @Test
    fun `valid recurrence config succeeds`() {
        assertTrue(rule.evaluate(context(), config) is Success)
    }

    @Test
    fun `no recurrence metadata passes silently`() {
        val ctx = context().copy(metadata = emptyMap())
        assertTrue(rule.evaluate(ctx, config) is Success)
    }

    @Test
    fun `weekCount exceeding maxWeeks fails`() {
        assertTrue(rule.evaluate(context(weekCount = 20), config) is Failure)
    }

    @Test
    fun `weekCount less than 1 fails`() {
        assertTrue(rule.evaluate(context(weekCount = 0), config) is Failure)
    }

    @Test
    fun `dayOfWeek mismatch fails`() {
        // Booking is on Monday but recurrence says WEDNESDAY
        assertTrue(rule.evaluate(context(dayOfWeek = "WEDNESDAY"), config) is Failure)
    }

    @Test
    fun `invalid dayOfWeek string returns failure`() {
        assertTrue(rule.evaluate(context(dayOfWeek = "NOTADAY"), config) is Failure)
    }

    @Test
    fun `missing recurrence fields returns failure`() {
        val ctx = context().copy(metadata = mapOf("recurrence" to mapOf("dayOfWeek" to "MONDAY")))
        assertTrue(rule.evaluate(ctx, config) is Failure)
    }

    @Test
    fun `failure message on weekCount over limit describes max`() {
        val result = rule.evaluate(context(weekCount = 20), config) as Failure
        assertTrue(result.message.contains("12"), "Should mention max weeks: ${result.message}")
    }

    @Test
    fun `all dayOfWeek values are accepted when matching`() {
        val days = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        val baseDate = LocalDateTime.of(2025, 1, 6, 10, 0)  // Monday
        for ((offset, day) in days.withIndex()) {
            val ctx = BookingContext(
                tenantId  = UUID.randomUUID(),
                resource  = resource,
                userId    = "user",
                startTime = baseDate.plusDays(offset.toLong()),
                endTime   = baseDate.plusDays(offset.toLong()).plusHours(1),
                metadata  = mapOf("recurrence" to mapOf("dayOfWeek" to day, "weekCount" to 2))
            )
            assertTrue(rule.evaluate(ctx, config) is Success, "Expected $day to match")
        }
    }
}
