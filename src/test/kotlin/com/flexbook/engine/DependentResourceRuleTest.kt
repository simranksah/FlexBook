package com.flexbook.engine

import com.flexbook.domain.BookingStatus
import com.flexbook.domain.Resource
import com.flexbook.engine.RuleResult.Failure
import com.flexbook.engine.RuleResult.Success
import com.flexbook.engine.RuleResult.Transition
import com.flexbook.engine.impl.DependentResourceRule
import com.flexbook.repository.BookingRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.UUID

class DependentResourceRuleTest {

    private val bookingRepository = mock<BookingRepository>()
    private val rule = DependentResourceRule(bookingRepository)
    private val resource = Resource(tenantId = UUID.randomUUID(), name = "Tennis Court", type = "COURT")
    private val tenantId = UUID.randomUUID()
    private val trainerResourceId = UUID.fromString("00000000-0000-0000-0001-000000000003")
    private val config = RuleConfig(
        "DEPENDENT_RESOURCE",
        mapOf("requiredResourceId" to trainerResourceId.toString()),
        priority = 10
    )

    private fun context(userId: String?, startHour: Int = 10) = BookingContext(
        tenantId  = tenantId,
        resource  = resource,
        userId    = userId,
        startTime = LocalDateTime.of(2025, 1, 15, startHour, 0),
        endTime   = LocalDateTime.of(2025, 1, 15, startHour + 1, 0)
    )

    @Test
    fun `required resource is booked by same user succeeds`() {
        whenever(bookingRepository.countUserOverlappingForResource(any(), any(), any(), any())).thenReturn(1L)
        assertTrue(rule.evaluate(context("user-123"), config) is Success)
    }

    @Test
    fun `required resource not booked transitions to PENDING`() {
        whenever(bookingRepository.countUserOverlappingForResource(any(), any(), any(), any())).thenReturn(0L)
        val result = rule.evaluate(context("user-123"), config)
        assertTrue(result is Transition)
        assertEquals(BookingStatus.PENDING, (result as Transition).status)
    }

    @Test
    fun `null userId skips rule`() {
        val result = rule.evaluate(context(userId = null), config)
        assertTrue(result is Success)
        verify(bookingRepository, never()).countUserOverlappingForResource(any(), any(), any(), any())
    }

    @Test
    fun `missing requiredResourceId param returns failure`() {
        val badConfig = config.copy(params = emptyMap())
        val result = rule.evaluate(context("user-123"), badConfig)
        assertTrue(result is Failure)
    }

    @Test
    fun `invalid UUID format for requiredResourceId returns failure`() {
        val badConfig = config.copy(params = mapOf("requiredResourceId" to "not-a-uuid"))
        val result = rule.evaluate(context("user-123"), badConfig)
        assertTrue(result is Failure)
    }

    @Test
    fun `transition reason is descriptive`() {
        whenever(bookingRepository.countUserOverlappingForResource(any(), any(), any(), any())).thenReturn(0L)
        val result = rule.evaluate(context("user-123"), config) as Transition
        assertTrue(result.reason.contains(trainerResourceId.toString()))
        assertTrue(result.reason.contains("user-123"))
    }
}
