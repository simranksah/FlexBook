package com.flexbook.api

import com.flexbook.api.dto.CreateTenantRequest
import com.flexbook.api.dto.TenantResponse
import com.flexbook.service.TenantService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/tenants")
class TenantController(private val tenantService: TenantService) {

    /** POST /tenants — Create a new tenant. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTenant(@Valid @RequestBody request: CreateTenantRequest): TenantResponse =
        tenantService.createTenant(request)

    /** GET /tenants/{id} — Fetch tenant details. */
    @GetMapping("/{id}")
    fun getTenant(@PathVariable id: UUID): TenantResponse =
        tenantService.getTenant(id)
}
