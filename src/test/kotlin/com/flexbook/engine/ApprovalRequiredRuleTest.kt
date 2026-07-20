package com.flexbook.engine

import com.flexbook.domain.BookingStatus
import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.RuleResult.Transition
import com.flexbook.engine.impl.ApprovalRequiredRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ApprovalRequiredRuleTest {

    private val rule = ApprovalRequiredRule()
    private val resource = Resource(tenantId = UUID.randomUUID(), name = "Hall A", type = "ROOM")
    private val config = RuleConfig("APPROVAL_REQUIRED", mapOf("threshold" to 10), priority = 20)

    private fun context(groupSize: Int) = BookingContext(
        tenantId  = UUID.randomUUID(),
        resource  = resource,
        userId    = null,
        startTime = LocalDateTime.of(2025, 1, 15, 9, 0),
        endTime   = LocalDateTime.of(2025, 1, 15, 11, 0),
        metadata  = mapOf("groupSize" to groupSize)
    )

    @Test
    fun `small group below threshold is confirmed`() {
        val result = rule.evaluate(context(groupSize = 9), config)
        assertTrue(result is Success)
    }

    @Test
    fun `group exactly at threshold requires approval`() {
        val result = rule.evaluate(context(groupSize = 10), config)
        assertTrue(result is Transition)
        assertEquals(BookingStatus.PENDING, (result as Transition).status)
    }

    @Test
    fun `group above threshold requires approval`() {
        val result = rule.evaluate(context(groupSize = 25), config)
        assertTrue(result is Transition)
        assertEquals(BookingStatus.PENDING, (result as Transition).status)
    }

    @Test
    fun `missing groupSize metadata defaults to zero and succeeds`() {
        val contextWithNoGroup = BookingContext(
            tenantId  = UUID.randomUUID(),
            resource  = resource,
            userId    = null,
            startTime = LocalDateTime.of(2025, 1, 15, 9, 0),
            endTime   = LocalDateTime.of(2025, 1, 15, 11, 0)
        )
        val result = rule.evaluate(contextWithNoGroup, config)
        assertTrue(result is Success)
    }
}
