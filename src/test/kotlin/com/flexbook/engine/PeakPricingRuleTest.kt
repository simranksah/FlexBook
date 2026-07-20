package com.flexbook.engine

import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.PriceModifier
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.impl.PeakPricingRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PeakPricingRuleTest {

    private val rule     = PeakPricingRule()
    private val resource = Resource(tenantId = UUID.randomUUID(), name = "Gym", type = "FACILITY")
    private val config   = RuleConfig(
        "PEAK_PRICING",
        mapOf("multiplier" to 2.0, "startHour" to 17, "endHour" to 21),
        priority = 5
    )

    private fun context(hour: Int, dayOfWeek: String = "WEDNESDAY") = BookingContext(
        tenantId  = UUID.randomUUID(),
        resource  = resource,
        userId    = null,
        startTime = LocalDateTime.parse("2025-01-15T${hour.toString().padStart(2, '0')}:00:00"), // Wednesday
        endTime   = LocalDateTime.parse("2025-01-15T${hour.toString().padStart(2, '0')}:00:00").plusHours(1)
    )

    @Test
    fun `booking during peak hours gets multiplier`() {
        val result = rule.evaluate(context(hour = 18), config)
        assertTrue(result is PriceModifier)
        assertEquals(2.0, (result as PriceModifier).multiplier)
    }

    @Test
    fun `booking outside peak hours returns Success`() {
        assertTrue(rule.evaluate(context(hour = 14), config) is Success)
    }

    @Test
    fun `booking at exact peak start hour gets multiplier`() {
        assertTrue(rule.evaluate(context(hour = 17), config) is PriceModifier)
    }

    @Test
    fun `booking at peak end hour does NOT get multiplier (exclusive)`() {
        // endHour=21 is exclusive
        assertTrue(rule.evaluate(context(hour = 21), config) is Success)
    }

    @Test
    fun `daysOfWeek filter restricts to specific days`() {
        val weekendConfig = config.copy(
            params = mapOf("multiplier" to 1.5, "startHour" to 10, "endHour" to 20, "daysOfWeek" to listOf("SATURDAY", "SUNDAY"))
        )
        // 2025-01-15 is a Wednesday — should not match
        assertTrue(rule.evaluate(context(hour = 12), weekendConfig) is Success)
    }

    @Test
    fun `no daysOfWeek means every day`() {
        val everyDayConfig = config.copy(params = mapOf("multiplier" to 2.0, "startHour" to 17, "endHour" to 21))
        assertTrue(rule.evaluate(context(hour = 18), everyDayConfig) is PriceModifier)
    }

    @Test
    fun `missing multiplier param returns failure`() {
        val badConfig = config.copy(params = mapOf("startHour" to 17, "endHour" to 21))
        assertTrue(rule.evaluate(context(hour = 18), badConfig) is RuleResult.Failure)
    }

    @Test
    fun `PriceModifier reason includes context info`() {
        val result = rule.evaluate(context(hour = 18), config) as PriceModifier
        assertTrue(result.reason.isNotBlank())
        assertTrue(result.reason.contains("2.0") || result.reason.contains("2×") || result.reason.contains("Peak"))
    }
}
