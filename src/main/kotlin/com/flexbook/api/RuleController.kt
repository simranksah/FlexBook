package com.flexbook.api

import com.flexbook.api.dto.CreateRuleRequest
import com.flexbook.api.dto.RuleResponse
import com.flexbook.service.RuleService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/tenants/{tenantId}/rules")
class RuleController(private val ruleService: RuleService) {

    /** POST /tenants/{tenantId}/rules — Create/compose a rule for a tenant. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRule(
        @PathVariable tenantId: UUID,
        @Valid @RequestBody request: CreateRuleRequest
    ): RuleResponse = ruleService.createRule(tenantId, request)

    /** GET /tenants/{tenantId}/rules — List all rules for a tenant. */
    @GetMapping
    fun listRules(@PathVariable tenantId: UUID): List<RuleResponse> =
        ruleService.listRules(tenantId)

    /** DELETE /tenants/{tenantId}/rules/{id} — Remove a rule configuration. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRule(
        @PathVariable tenantId: UUID,
        @PathVariable id: UUID
    ) = ruleService.deleteRule(tenantId, id)
}
