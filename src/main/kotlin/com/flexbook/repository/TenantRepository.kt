package com.flexbook.repository

import com.flexbook.domain.Tenant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TenantRepository : JpaRepository<Tenant, UUID> {
    fun findByIdAndEnabledTrue(id: UUID): Tenant?
    fun existsByName(name: String): Boolean
}
