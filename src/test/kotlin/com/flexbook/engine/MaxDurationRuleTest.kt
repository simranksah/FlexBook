package com.flexbook.engine

import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.Failure
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.impl.MaxDurationRule
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MaxDurationRuleTest {

    private val rule     = MaxDurationRule()
    private val resource = Resource(tenantId = UUID.randomUUID(), name = "Conf Room", type = "ROOM")
    private val config   = RuleConfig("MAX_DURATION", mapOf("maxMinutes" to 240), priority = 10)

    private fun context(durationMinutes: Long) = BookingContext(
        tenantId  = UUID.randomUUID(),
        resource  = resource,
        userId    = null,
        startTime = LocalDateTime.of(2025, 1, 15, 9, 0),
        endTime   = LocalDateTime.of(2025, 1, 15, 9, 0).plusMinutes(durationMinutes)
    )

    @Test
    fun `booking under limit succeeds`() {
        assertTrue(rule.evaluate(context(120), config) is Success)
    }

    @Test
    fun `booking exactly at limit succeeds`() {
        assertTrue(rule.evaluate(context(240), config) is Success)
    }

    @Test
    fun `booking over limit fails`() {
        assertTrue(rule.evaluate(context(241), config) is Failure)
    }

    @Test
    fun `failure message includes duration and limit`() {
        val result = rule.evaluate(context(300), config) as Failure
        assertTrue(result.message.contains("300"), "Should mention actual duration")
        assertTrue(result.message.contains("240"), "Should mention max limit")
    }

    @Test
    fun `missing maxMinutes param returns failure`() {
        val badConfig = config.copy(params = emptyMap())
        assertTrue(rule.evaluate(context(60), badConfig) is Failure)
    }
}
