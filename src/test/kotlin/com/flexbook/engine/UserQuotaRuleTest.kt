package com.flexbook.engine

import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.Failure
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.impl.UserQuotaRule
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

class UserQuotaRuleTest {

    private val bookingRepository = mock<BookingRepository>()
    private val rule              = UserQuotaRule(bookingRepository)
    private val resource          = Resource(tenantId = UUID.randomUUID(), name = "Clinic", type = "ROOM")
    private val tenantId          = UUID.randomUUID()
    private val config            = RuleConfig("USER_QUOTA", mapOf("maxBookings" to 2, "periodDays" to 7), priority = 20)

    private fun context(userId: String?) = BookingContext(
        tenantId  = tenantId,
        resource  = resource,
        userId    = userId,
        startTime = LocalDateTime.of(2025, 1, 15, 9, 0),
        endTime   = LocalDateTime.of(2025, 1, 15, 10, 0)
    )

    @Test
    fun `user under quota succeeds`() {
        whenever(bookingRepository.countUserBookingsInPeriod(any(), any(), any())).thenReturn(1L)
        assertTrue(rule.evaluate(context("user-123"), config) is Success)
    }

    @Test
    fun `user at quota fails`() {
        whenever(bookingRepository.countUserBookingsInPeriod(any(), any(), any())).thenReturn(2L)
        assertTrue(rule.evaluate(context("user-123"), config) is Failure)
    }

    @Test
    fun `user above quota fails`() {
        whenever(bookingRepository.countUserBookingsInPeriod(any(), any(), any())).thenReturn(5L)
        assertTrue(rule.evaluate(context("user-123"), config) is Failure)
    }

    @Test
    fun `null userId skips rule and returns Success`() {
        val result = rule.evaluate(context(userId = null), config)
        assertTrue(result is Success)
        // Repository should NOT have been queried — no userId to look up
        verify(bookingRepository, never()).countUserBookingsInPeriod(any(), any(), any())
    }

    @Test
    fun `first booking by user under quota succeeds`() {
        whenever(bookingRepository.countUserBookingsInPeriod(any(), any(), any())).thenReturn(0L)
        assertTrue(rule.evaluate(context("new-user"), config) is Success)
    }

    @Test
    fun `failure message includes userId and quota`() {
        whenever(bookingRepository.countUserBookingsInPeriod(any(), any(), any())).thenReturn(2L)
        val result = rule.evaluate(context("user-123"), config) as Failure
        assertTrue(result.message.contains("user-123"), "Should mention userId")
        assertTrue(result.message.contains("2"), "Should mention quota limit")
    }

    @Test
    fun `missing maxBookings param returns failure`() {
        val badConfig = config.copy(params = mapOf("periodDays" to 7))
        assertTrue(rule.evaluate(context("user-123"), badConfig) is Failure)
    }
}
