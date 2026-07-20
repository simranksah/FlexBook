package com.flexbook.engine

import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.Failure
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.impl.BlackoutPeriodRule
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class BlackoutPeriodRuleTest {

    private val rule     = BlackoutPeriodRule()
    private val resource = Resource(tenantId = UUID.randomUUID(), name = "Office", type = "DESK")
    private val config   = RuleConfig(
        "BLACKOUT_PERIOD",
        mapOf("startDate" to "2025-12-25", "endDate" to "2026-01-03"),
        priority = 10
    )

    private fun context(date: LocalDateTime) = BookingContext(
        tenantId  = UUID.randomUUID(),
        resource  = resource,
        userId    = null,
        startTime = date,
        endTime   = date.plusHours(1)
    )

    @Test
    fun `booking outside blackout succeeds`() {
        assertTrue(rule.evaluate(context(LocalDateTime.of(2025, 12, 24, 10, 0)), config) is Success)
    }

    @Test
    fun `booking on first day of blackout fails`() {
        assertTrue(rule.evaluate(context(LocalDateTime.of(2025, 12, 25, 10, 0)), config) is Failure)
    }

    @Test
    fun `booking on last day of blackout fails`() {
        assertTrue(rule.evaluate(context(LocalDateTime.of(2026, 1, 3, 10, 0)), config) is Failure)
    }

    @Test
    fun `booking the day after blackout ends succeeds`() {
        assertTrue(rule.evaluate(context(LocalDateTime.of(2026, 1, 4, 10, 0)), config) is Success)
    }

    @Test
    fun `booking mid-blackout fails`() {
        assertTrue(rule.evaluate(context(LocalDateTime.of(2025, 12, 30, 10, 0)), config) is Failure)
    }

    @Test
    fun `invalid date format returns failure not exception`() {
        val badConfig = config.copy(params = mapOf("startDate" to "25-12-2025", "endDate" to "2026-01-03"))
        assertTrue(rule.evaluate(context(LocalDateTime.of(2025, 12, 25, 10, 0)), badConfig) is Failure)
    }

    @Test
    fun `missing startDate param returns failure`() {
        val badConfig = config.copy(params = mapOf("endDate" to "2026-01-03"))
        assertTrue(rule.evaluate(context(LocalDateTime.of(2025, 12, 25, 10, 0)), badConfig) is Failure)
    }
}
