package com.flexbook.engine

import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.Failure
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.impl.BufferTimeRule
import com.flexbook.repository.BookingRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.UUID

class BufferTimeRuleTest {

    private val bookingRepository = mock<BookingRepository>()
    private val rule              = BufferTimeRule(bookingRepository)
    private val resource          = Resource(tenantId = UUID.randomUUID(), name = "Studio A", type = "STUDIO")
    private val config            = RuleConfig("BUFFER_TIME", mapOf("bufferMinutes" to 15), priority = 10)

    private fun context(startHour: Int, startMin: Int = 0, durationMin: Int = 60) = BookingContext(
        tenantId  = UUID.randomUUID(),
        resource  = resource,
        userId    = null,
        startTime = LocalDateTime.of(2025, 1, 15, startHour, startMin),
        endTime   = LocalDateTime.of(2025, 1, 15, startHour, startMin).plusMinutes(durationMin.toLong())
    )

    @Test
    fun `no adjacent bookings — succeeds`() {
        whenever(bookingRepository.countBufferConflicts(any(), any(), any(), any(), any())).thenReturn(0L)
        assertTrue(rule.evaluate(context(10), config) is Success)
    }

    @Test
    fun `booking ending in buffer zone before slot — fails`() {
        whenever(bookingRepository.countBufferConflicts(any(), any(), any(), any(), any())).thenReturn(1L)
        assertTrue(rule.evaluate(context(10), config) is Failure)
    }

    @Test
    fun `booking starting in buffer zone after slot — fails`() {
        whenever(bookingRepository.countBufferConflicts(any(), any(), any(), any(), any())).thenReturn(1L)
        assertTrue(rule.evaluate(context(10), config) is Failure)
    }

    @Test
    fun `booking outside buffer zone — succeeds`() {
        whenever(bookingRepository.countBufferConflicts(any(), any(), any(), any(), any())).thenReturn(0L)
        assertTrue(rule.evaluate(context(13), config) is Success)
    }

    @Test
    fun `missing bufferMinutes param returns failure`() {
        val badConfig = config.copy(params = emptyMap())
        // Repository should not be called
        assertTrue(rule.evaluate(context(10), badConfig) is Failure)
    }

    @Test
    fun `failure message mentions buffer duration`() {
        whenever(bookingRepository.countBufferConflicts(any(), any(), any(), any(), any())).thenReturn(1L)
        val result = rule.evaluate(context(10), config) as Failure
        assertTrue(result.message.contains("15"), "Should mention buffer duration: ${result.message}")
    }
}
