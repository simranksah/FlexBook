package com.flexbook.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.flexbook.api.dto.CreateRuleRequest
import com.flexbook.api.dto.RuleResponse
import com.flexbook.domain.RuleConfiguration
import com.flexbook.engine.RuleRegistry
import com.flexbook.repository.RuleConfigurationRepository
import com.flexbook.repository.TenantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RuleService(
    private val ruleConfigRepository: RuleConfigurationRepository,
    private val tenantRepository: TenantRepository,
    private val ruleRegistry: RuleRegistry,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun createRule(tenantId: UUID, request: CreateRuleRequest): RuleResponse {
        tenantRepository.findByIdAndEnabledTrue(tenantId)
            ?: throw NotFoundException("Tenant $tenantId not found or disabled")

        // Validate the rule type is known
        runCatching { ruleRegistry.get(request.ruleType) }.getOrElse {
            throw ValidationException(
                "Unknown rule type '${request.ruleType}'. " +
                "Available: ${ruleRegistry.knownTypes().sorted()}"
            )
        }

        val config = RuleConfiguration(
            tenantId     = tenantId,
            resourceId   = request.resourceId,
            resourceType = request.resourceType,
            ruleType     = request.ruleType,
            params       = objectMapper.writeValueAsString(request.params),
            priority     = request.priority
        )
        val saved = ruleConfigRepository.save(config)
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun listRules(tenantId: UUID): List<RuleResponse> {
        tenantRepository.findByIdAndEnabledTrue(tenantId)
            ?: throw NotFoundException("Tenant $tenantId not found or disabled")
        return ruleConfigRepository.findByTenantIdAndEnabledTrue(tenantId).map { it.toResponse() }
    }

    @Transactional
    fun deleteRule(tenantId: UUID, ruleId: UUID) {
        val rule = ruleConfigRepository.findByIdAndTenantId(ruleId, tenantId)
            ?: throw NotFoundException("Rule $ruleId not found for tenant $tenantId")
        ruleConfigRepository.delete(rule)
    }

    private fun RuleConfiguration.toResponse() = RuleResponse(
        id           = id,
        tenantId     = tenantId,
        ruleType     = ruleType,
        params       = objectMapper.readValue(params, Map::class.java)
                           .mapKeys { it.key.toString() }
                           .mapValues { it.value as Any },
        priority     = priority,
        resourceId   = resourceId,
        resourceType = resourceType,
        enabled      = enabled,
        createdAt    = createdAt
    )
}
