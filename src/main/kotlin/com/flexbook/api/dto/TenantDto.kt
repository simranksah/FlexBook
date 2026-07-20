package com.flexbook.api.dto

import com.flexbook.domain.Tenant
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime
import java.util.UUID

data class CreateTenantRequest(
    @field:NotBlank val name: String
)

data class TenantResponse(
    val id: UUID,
    val name: String,
    val enabled: Boolean,
    val createdAt: LocalDateTime
)

fun Tenant.toResponse() = TenantResponse(id = id, name = name, enabled = enabled, createdAt = createdAt)
