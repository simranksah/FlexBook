package com.flexbook.service

import com.flexbook.api.dto.CreateTenantRequest
import com.flexbook.api.dto.TenantResponse
import com.flexbook.api.dto.toResponse
import com.flexbook.domain.Tenant
import com.flexbook.repository.TenantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TenantService(private val tenantRepository: TenantRepository) {

    @Transactional
    fun createTenant(request: CreateTenantRequest): TenantResponse {
        if (tenantRepository.existsByName(request.name)) {
            throw ValidationException("Tenant with name '${request.name}' already exists")
        }
        return tenantRepository.save(Tenant(name = request.name)).toResponse()
    }

    @Transactional(readOnly = true)
    fun getTenant(tenantId: UUID): TenantResponse =
        tenantRepository.findByIdAndEnabledTrue(tenantId)?.toResponse()
            ?: throw NotFoundException("Tenant $tenantId not found or disabled")
}
