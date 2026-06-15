package com.flexbook.repository

import com.flexbook.domain.RuleConfiguration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RuleConfigurationRepository : JpaRepository<RuleConfiguration, UUID> {

    fun findByIdAndTenantId(id: UUID, tenantId: UUID): RuleConfiguration?

    fun findByTenantIdAndEnabledTrue(tenantId: UUID): List<RuleConfiguration>

    /**
     * Fetches all enabled rules applicable to a booking, across three scopes:
     *   1. Resource-specific (resourceId matches)
     *   2. Resource-type-wide (resourceType matches, resourceId is null)
     *   3. Tenant-wide (both resourceId and resourceType are null)
     *
     * Ordered by priority DESC so the engine evaluates highest-priority rules first,
     * enabling correct "first transition wins" conflict resolution.
     */
    @Query("""
        SELECT rc FROM RuleConfiguration rc
        WHERE rc.tenantId = :tenantId
          AND rc.enabled = true
          AND (
              rc.resourceId = :resourceId
              OR (rc.resourceType = :resourceType AND rc.resourceId IS NULL)
              OR (rc.resourceId IS NULL AND rc.resourceType IS NULL)
          )
        ORDER BY rc.priority DESC
    """)
    fun findAllApplicable(
        @Param("tenantId") tenantId: UUID,
        @Param("resourceId") resourceId: UUID,
        @Param("resourceType") resourceType: String
    ): List<RuleConfiguration>
}
