package com.flexbook.engine

import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.Failure
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.impl.CapacityRule
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class CapacityRuleTest {

    private val rule = CapacityRule()
    private val config = RuleConfig("CAPACITY", emptyMap(), priority = 30)

    private fun context(capacity: Int, currentBookings: Int) = BookingContext(
        tenantId  = UUID.randomUUID(),
        resource  = Resource(tenantId = UUID.randomUUID(), name = "Conf Room", type = "ROOM", capacity = capacity),
        userId    = null,
        startTime = LocalDateTime.of(2025, 1, 15, 9, 0),
        endTime   = LocalDateTime.of(2025, 1, 15, 10, 0),
        metadata  = mapOf("currentBookings" to currentBookings)
    )

    @Test
    fun `booking into empty resource succeeds`() {
        val result = rule.evaluate(context(capacity = 3, currentBookings = 0), config)
        assertTrue(result is Success)
    }

    @Test
    fun `booking when one slot remains succeeds`() {
        val result = rule.evaluate(context(capacity = 3, currentBookings = 2), config)
        assertTrue(result is Success)
    }

    @Test
    fun `booking when at capacity fails`() {
        val result = rule.evaluate(context(capacity = 3, currentBookings = 3), config)
        assertTrue(result is Failure)
    }

    @Test
    fun `single-capacity resource fails on second booking`() {
        val result = rule.evaluate(context(capacity = 1, currentBookings = 1), config)
        assertTrue(result is Failure)
    }

    @Test
    fun `failure message includes capacity and current count`() {
        val result = rule.evaluate(context(capacity = 2, currentBookings = 2), config) as Failure
        assertTrue(result.message.contains("2"), "Message should mention capacity: ${result.message}")
    }
}
