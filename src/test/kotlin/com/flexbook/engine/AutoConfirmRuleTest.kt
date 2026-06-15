package com.flexbook.engine

import com.flexbook.domain.BookingStatus
import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.RuleResult.Transition
import com.flexbook.engine.impl.AutoConfirmRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class AutoConfirmRuleTest {

    private val rule     = AutoConfirmRule()
    private val resource = Resource(tenantId = UUID.randomUUID(), name = "Room", type = "ROOM")
    private val config   = RuleConfig(
        "AUTO_CONFIRM",
        mapOf("maxGroupSize" to 5, "startHour" to 9, "endHour" to 17),
        priority = 100
    )

    private fun context(groupSize: Int, startHour: Int = 10) = BookingContext(
        tenantId  = UUID.randomUUID(),
        resource  = resource,
        userId    = null,
        startTime = LocalDateTime.of(2025, 1, 15, startHour, 0),
        endTime   = LocalDateTime.of(2025, 1, 15, startHour + 1, 0),
        metadata  = mapOf("groupSize" to groupSize)
    )

    @Test
    fun `small group within business hours auto-confirms`() {
        val result = rule.evaluate(context(groupSize = 4), config)
        assertTrue(result is Transition)
        assertEquals(BookingStatus.CONFIRMED, (result as Transition).status)
    }

    @Test
    fun `group at maxGroupSize threshold does NOT auto-confirm (exclusive)`() {
        // groupSize < maxGroupSize, so groupSize=5 with maxGroupSize=5 → NOT confirmed
        val result = rule.evaluate(context(groupSize = 5), config)
        assertTrue(result is Success, "Expected Success when groupSize == maxGroupSize (condition is <, not <=)")
    }

    @Test
    fun `large group returns Success (lets other rules handle it)`() {
        val result = rule.evaluate(context(groupSize = 20), config)
        assertTrue(result is Success)
    }

    @Test
    fun `booking outside business hours returns Success`() {
        val result = rule.evaluate(context(groupSize = 2, startHour = 7), config)
        assertTrue(result is Success)
    }

    @Test
    fun `no conditions in config auto-confirms everything`() {
        val openConfig = config.copy(params = emptyMap())
        val result = rule.evaluate(context(groupSize = 100), openConfig)
        assertTrue(result is Transition)
        assertEquals(BookingStatus.CONFIRMED, (result as Transition).status)
    }

    @Test
    fun `auto-confirm transition reason is descriptive`() {
        val result = rule.evaluate(context(groupSize = 2), config) as Transition
        assertTrue(result.reason.isNotBlank())
    }
}
