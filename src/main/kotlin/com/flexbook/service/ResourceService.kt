package com.flexbook.service

import com.flexbook.api.dto.CreateResourceRequest
import com.flexbook.api.dto.ResourceResponse
import com.flexbook.api.dto.toResponse
import com.flexbook.domain.Resource
import com.flexbook.repository.ResourceRepository
import com.flexbook.repository.TenantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ResourceService(
    private val resourceRepository: ResourceRepository,
    private val tenantRepository: TenantRepository
) {

    @Transactional
    fun createResource(tenantId: UUID, request: CreateResourceRequest): ResourceResponse {
        tenantRepository.findByIdAndEnabledTrue(tenantId)
            ?: throw NotFoundException("Tenant $tenantId not found or disabled")

        val resource = Resource(
            tenantId         = tenantId,
            name             = request.name,
            type             = request.type,
            capacity         = request.capacity,
            basePricePerHour = request.basePricePerHour
        )
        return resourceRepository.save(resource).toResponse()
    }

    @Transactional(readOnly = true)
    fun getResource(tenantId: UUID, resourceId: UUID): ResourceResponse =
        resourceRepository.findByIdAndTenantIdAndEnabledTrue(resourceId, tenantId)?.toResponse()
            ?: throw NotFoundException("Resource $resourceId not found for tenant $tenantId")
}
