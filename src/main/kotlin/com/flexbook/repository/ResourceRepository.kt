package com.flexbook.repository

import com.flexbook.domain.Resource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ResourceRepository : JpaRepository<Resource, UUID> {
    fun findByIdAndTenantIdAndEnabledTrue(id: UUID, tenantId: UUID): Resource?
}
