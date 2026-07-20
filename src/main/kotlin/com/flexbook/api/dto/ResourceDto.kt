package com.flexbook.api.dto

import com.flexbook.domain.Resource
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime
import java.util.UUID

data class CreateResourceRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val type: String,
    @field:Min(1) val capacity: Int = 1,
    /** Base price per hour in smallest currency unit (paise, cents). Null = unpriced resource. */
    val basePricePerHour: Long? = null
)

data class ResourceResponse(
    val id: UUID,
    val tenantId: UUID,
    val name: String,
    val type: String,
    val capacity: Int,
    val basePricePerHour: Long?,
    val enabled: Boolean,
    val createdAt: LocalDateTime
)

fun Resource.toResponse() = ResourceResponse(
    id = id,
    tenantId = tenantId,
    name = name,
    type = type,
    capacity = capacity,
    basePricePerHour = basePricePerHour,
    enabled = enabled,
    createdAt = createdAt
)
