package com.flexbook.engine

import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.Failure
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.impl.CooldownRule
import com.flexbook.repository.BookingRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.UUID

class CooldownRuleTest {

    private val bookingRepository = mock<BookingRepository>()
    private val rule = CooldownRule(bookingRepository)
    private val resource = Resource(tenantId = UUID.randomUUID(), name = "Studio A", type = "STUDIO")
    private val tenantId = UUID.randomUUID()
    private val config = RuleConfig("COOLDOWN", mapOf("cooldownHours" to 24), priority = 10)

    private fun context(userId: String?, startHour: Int = 10) = BookingContext(
        tenantId  = tenantId,
        resource  = resource,
        userId    = userId,
        startTime = LocalDateTime.of(2025, 1, 16, startHour, 0),
        endTime   = LocalDateTime.of(2025, 1, 16, startHour + 1, 0)
    )

    @Test
    fun `no previous booking within cooldown succeeds`() {
        whenever(bookingRepository.countCooldownConflicts(any(), any(), any(), any())).thenReturn(0L)
        assertTrue(rule.evaluate(context("user-123"), config) is Success)
    }

    @Test
    fun `previous booking within cooldown fails`() {
        whenever(bookingRepository.countCooldownConflicts(any(), any(), any(), any())).thenReturn(1L)
        assertTrue(rule.evaluate(context("user-123"), config) is Failure)
    }

    @Test
    fun `null userId skips rule`() {
        val result = rule.evaluate(context(userId = null), config)
        assertTrue(result is Success)
        verify(bookingRepository, never()).countCooldownConflicts(any(), any(), any(), any())
    }

    @Test
    fun `multiple violations still fail`() {
        whenever(bookingRepository.countCooldownConflicts(any(), any(), any(), any())).thenReturn(3L)
        assertTrue(rule.evaluate(context("user-123"), config) is Failure)
    }

    @Test
    fun `missing cooldownHours param returns failure`() {
        val badConfig = config.copy(params = emptyMap())
        assertTrue(rule.evaluate(context("user-123"), badConfig) is Failure)
    }

    @Test
    fun `failure message mentions cooldown hours and userId`() {
        whenever(bookingRepository.countCooldownConflicts(any(), any(), any(), any())).thenReturn(1L)
        val result = rule.evaluate(context("user-123"), config) as Failure
        assertTrue(result.message.contains("24"), "Should mention cooldown hours: ${result.message}")
        assertTrue(result.message.contains("user-123"), "Should mention userId: ${result.message}")
    }
}
