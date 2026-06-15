package com.flexbook.engine

import com.flexbook.domain.BookingStatus
import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.Failure
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.impl.TimeBoundaryRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class TimeBoundaryRuleTest {

    private val rule = TimeBoundaryRule()
    private val resource = Resource(
        tenantId = UUID.randomUUID(),
        name = "Conf Room",
        type = "ROOM"
    )
    private val config = RuleConfig(
        ruleType = "TIME_BOUNDARY",
        params = mapOf("startHour" to 8, "endHour" to 18),
        priority = 10
    )

    private fun context(startHour: Int, startMin: Int = 0, endHour: Int, endMin: Int = 0) =
        BookingContext(
            tenantId  = UUID.randomUUID(),
            resource  = resource,
            userId    = null,
            startTime = LocalDateTime.of(2025, 1, 15, startHour, startMin),
            endTime   = LocalDateTime.of(2025, 1, 15, endHour, endMin)
        )

    @Test
    fun `booking within window succeeds`() {
        val result = rule.evaluate(context(startHour = 9, endHour = 17), config)
        assertTrue(result is Success)
    }

    @Test
    fun `booking ending exactly on boundary succeeds`() {
        // 08:00–18:00 is allowed; a booking ending at 18:00 sharp is valid
        val result = rule.evaluate(context(startHour = 8, endHour = 18, endMin = 0), config)
        assertTrue(result is Success)
    }

    @Test
    fun `booking starting before window fails`() {
        val result = rule.evaluate(context(startHour = 7, endHour = 10), config)
        assertTrue(result is Failure)
    }

    @Test
    fun `booking ending after window fails`() {
        val result = rule.evaluate(context(startHour = 9, endHour = 19), config)
        assertTrue(result is Failure)
    }

    @Test
    fun `failure message is descriptive`() {
        val result = rule.evaluate(context(startHour = 7, endHour = 10), config) as Failure
        assertTrue(result.message.contains("08:00"), "Message should contain allowed start: ${result.message}")
        assertTrue(result.message.contains("18:00"), "Message should contain allowed end: ${result.message}")
    }

    @Test
    fun `missing startHour param returns failure`() {
        val badConfig = config.copy(params = mapOf("endHour" to 18))
        val result = rule.evaluate(context(startHour = 9, endHour = 17), badConfig)
        assertTrue(result is Failure)
    }
}
