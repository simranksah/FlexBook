package com.flexbook.integration

import com.flexbook.api.dto.BatchBookingRequest
import com.flexbook.api.dto.BookingRequest
import com.flexbook.api.dto.BookingResponse
import com.flexbook.api.dto.CreateResourceRequest
import com.flexbook.api.dto.CreateRuleRequest
import com.flexbook.api.dto.CreateTenantRequest
import com.flexbook.api.dto.PagedResponse
import com.flexbook.api.dto.ResourceResponse
import com.flexbook.api.dto.RuleResponse
import com.flexbook.api.dto.TenantResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.RequestEntity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FlexBookIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("flexbook_test")
            withUsername("test")
            withPassword("test")
        }

        @DynamicPropertySource
        @JvmStatic
        fun datasourceProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var rest: TestRestTemplate

    private fun url(path: String) = "http://localhost:$port$path"

    @Test
    fun `full tenant setup and booking lifecycle`() {
        // 1. Create tenant
        val tenant = rest.postForEntity(
            url("/tenants"),
            CreateTenantRequest(name = "it-tenant-${UUID.randomUUID()}"),
            TenantResponse::class.java
        )
        assertEquals(HttpStatus.CREATED, tenant.statusCode)
        val tenantId = tenant.body!!.id

        // 2. Create resource
        val resource = rest.postForEntity(
            url("/tenants/$tenantId/resources"),
            CreateResourceRequest(name = "IT Conf Room", type = "ROOM", capacity = 5, basePricePerHour = 10000),
            ResourceResponse::class.java
        )
        assertEquals(HttpStatus.CREATED, resource.statusCode)
        val resourceId = resource.body!!.id

        // 3. Create TIME_BOUNDARY rule
        rest.postForEntity(
            url("/tenants/$tenantId/rules"),
            CreateRuleRequest(ruleType = "TIME_BOUNDARY", params = mapOf("startHour" to 8, "endHour" to 18), priority = 10),
            RuleResponse::class.java
        )

        // 4. Valid booking (10am–12pm)
        val booking = rest.postForEntity(
            url("/tenants/$tenantId/bookings"),
            BookingRequest(resourceId, LocalDateTime.of(2025, 1, 15, 10, 0), LocalDateTime.of(2025, 1, 15, 12, 0), userId = "user"),
            BookingResponse::class.java
        )
        assertEquals(HttpStatus.CREATED, booking.statusCode)
        assertEquals("CONFIRMED", booking.body!!.status.name)

        // 5. Invalid booking (7am) → 422
        val bad = rest.postForEntity(
            url("/tenants/$tenantId/bookings"),
            BookingRequest(resourceId, LocalDateTime.of(2025, 1, 15, 7, 0), LocalDateTime.of(2025, 1, 15, 8, 0), userId = "user"),
            Any::class.java
        )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, bad.statusCode)
    }

    @Test
    fun `approval required transitions to PENDING`() {
        val tenant = rest.postForEntity(url("/tenants"), CreateTenantRequest("approval-${UUID.randomUUID()}"), TenantResponse::class.java)
        val tid = tenant.body!!.id
        val resource = rest.postForEntity(url("/tenants/$tid/resources"), CreateResourceRequest("Room", "ROOM"), ResourceResponse::class.java)
        val rid = resource.body!!.id

        rest.postForEntity(url("/tenants/$tid/rules"), CreateRuleRequest("APPROVAL_REQUIRED", mapOf("threshold" to 5), 10), RuleResponse::class.java)

        val booking = rest.postForEntity(
            url("/tenants/$tid/bookings"),
            BookingRequest(rid, LocalDateTime.of(2025, 1, 15, 9, 0), LocalDateTime.of(2025, 1, 15, 10, 0), userId = "u", metadata = mapOf("groupSize" to 10)),
            BookingResponse::class.java
        )
        assertEquals(HttpStatus.CREATED, booking.statusCode)
        assertEquals("PENDING", booking.body!!.status.name)
    }

    @Test
    fun `batch booking returns 207 with partial success`() {
        val tenant = rest.postForEntity(url("/tenants"), CreateTenantRequest("batch-${UUID.randomUUID()}"), TenantResponse::class.java)
        val tid = tenant.body!!.id
        val resource = rest.postForEntity(url("/tenants/$tid/resources"), CreateResourceRequest("Batch Room", "ROOM"), ResourceResponse::class.java)
        val rid = resource.body!!.id

        rest.postForEntity(url("/tenants/$tid/rules"), CreateRuleRequest("TIME_BOUNDARY", mapOf("startHour" to 8, "endHour" to 18), 10), RuleResponse::class.java)

        val batchRequest = BatchBookingRequest(listOf(
            BookingRequest(rid, LocalDateTime.of(2025, 1, 15, 9, 0), LocalDateTime.of(2025, 1, 15, 10, 0), userId = "u1"),
            BookingRequest(rid, LocalDateTime.of(2025, 1, 15, 7, 0), LocalDateTime.of(2025, 1, 15, 8, 0), userId = "u2"),
            BookingRequest(rid, LocalDateTime.of(2025, 1, 15, 10, 0), LocalDateTime.of(2025, 1, 15, 11, 0), userId = "u3")
        ))

        val response = rest.exchange(
            RequestEntity.post(URI(url("/tenants/$tid/bookings/batch"))).body(batchRequest),
            object : ParameterizedTypeReference<List<Map<String, Any>>>() {}
        )
        assertEquals(HttpStatus.MULTI_STATUS, response.statusCode)
        val results = response.body!!
        assertEquals(3, results.size)
        assertEquals(201, results[0]["httpStatus"])
        assertEquals(422, results[1]["httpStatus"])
        assertEquals(201, results[2]["httpStatus"])
    }

    @Test
    fun `query bookings with userId filter`() {
        val tenant = rest.postForEntity(url("/tenants"), CreateTenantRequest("query-${UUID.randomUUID()}"), TenantResponse::class.java)
        val tid = tenant.body!!.id
        val resource = rest.postForEntity(url("/tenants/$tid/resources"), CreateResourceRequest("Query Room", "ROOM"), ResourceResponse::class.java)
        val rid = resource.body!!.id

        rest.postForEntity(url("/tenants/$tid/bookings"), BookingRequest(rid, LocalDateTime.of(2025, 1, 15, 9, 0), LocalDateTime.of(2025, 1, 15, 10, 0), userId = "user-a"), BookingResponse::class.java)
        rest.postForEntity(url("/tenants/$tid/bookings"), BookingRequest(rid, LocalDateTime.of(2025, 1, 15, 10, 0), LocalDateTime.of(2025, 1, 15, 11, 0), userId = "user-b"), BookingResponse::class.java)

        val result = rest.exchange(
            RequestEntity.get(URI(url("/tenants/$tid/bookings?userId=user-a"))).build(),
            object : ParameterizedTypeReference<PagedResponse<BookingResponse>>() {}
        )
        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(1, result.body!!.content.size)
    }

    @Test
    fun `peak pricing computed correctly end-to-end`() {
        val tenant = rest.postForEntity(url("/tenants"), CreateTenantRequest("price-${UUID.randomUUID()}"), TenantResponse::class.java)
        val tid = tenant.body!!.id
        val resource = rest.postForEntity(url("/tenants/$tid/resources"), CreateResourceRequest("Price Room", "ROOM", basePricePerHour = 6000), ResourceResponse::class.java)
        val rid = resource.body!!.id

        rest.postForEntity(url("/tenants/$tid/rules"), CreateRuleRequest("PEAK_PRICING", mapOf("multiplier" to 2.0, "startHour" to 17, "endHour" to 21), 10), RuleResponse::class.java)

        val booking = rest.postForEntity(
            url("/tenants/$tid/bookings"),
            BookingRequest(rid, LocalDateTime.of(2025, 1, 15, 18, 0), LocalDateTime.of(2025, 1, 15, 19, 0), userId = "u"),
            BookingResponse::class.java
        )
        assertEquals(6000L, booking.body!!.basePrice)
        assertEquals(2.0, booking.body!!.priceMultiplier)
        assertEquals(12000L, booking.body!!.totalPrice)
    }

    @Test
    fun `cooldown rule blocks rapid rebooking by same user`() {
        val tenant = rest.postForEntity(url("/tenants"), CreateTenantRequest("cooldown-${UUID.randomUUID()}"), TenantResponse::class.java)
        val tid = tenant.body!!.id
        val resource = rest.postForEntity(url("/tenants/$tid/resources"), CreateResourceRequest("Cooldown Room", "ROOM"), ResourceResponse::class.java)
        val rid = resource.body!!.id

        rest.postForEntity(url("/tenants/$tid/rules"), CreateRuleRequest("COOLDOWN", mapOf("cooldownHours" to 24), 10), RuleResponse::class.java)

        rest.postForEntity(
            url("/tenants/$tid/bookings"),
            BookingRequest(rid, LocalDateTime.of(2025, 1, 15, 10, 0), LocalDateTime.of(2025, 1, 15, 11, 0), userId = "cd-user"),
            BookingResponse::class.java
        )

        val second = rest.postForEntity(
            url("/tenants/$tid/bookings"),
            BookingRequest(rid, LocalDateTime.of(2025, 1, 16, 9, 0), LocalDateTime.of(2025, 1, 16, 10, 0), userId = "cd-user"),
            Any::class.java
        )
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, second.statusCode)
    }
}
