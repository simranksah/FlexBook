package com.flexbook.api

import com.flexbook.api.dto.CreateResourceRequest
import com.flexbook.api.dto.ResourceResponse
import com.flexbook.service.ResourceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/tenants/{tenantId}/resources")
class ResourceController(private val resourceService: ResourceService) {

    /** POST /tenants/{tenantId}/resources — Create a resource for a tenant. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createResource(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: CreateResourceRequest
    ): ResourceResponse = resourceService.createResource(tenantId, request)

    /** GET /tenants/{tenantId}/resources/{id} — Fetch resource details. */
    @GetMapping("/{id}")
    fun getResource(
        @PathVariable tenantId: UUID,
        @PathVariable id: UUID
    ): ResourceResponse = resourceService.getResource(tenantId, id)
}
