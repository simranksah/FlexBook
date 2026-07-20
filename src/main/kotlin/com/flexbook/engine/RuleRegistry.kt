package com.flexbook.engine

import org.springframework.stereotype.Component

/**
 * Auto-discovers all [BookingRule] Spring beans and exposes them by type string.
 * Adding a new rule only requires creating a @Component that implements BookingRule.
 */
@Component
class RuleRegistry(rules: List<BookingRule>) {

    private val registry: Map<String, BookingRule> = rules.associateBy { it.type }

    fun get(type: String): BookingRule = registry[type]
        ?: throw IllegalArgumentException(
            "No rule implementation found for type='$type'. " +
            "Available: ${registry.keys.sorted()}"
        )

    fun knownTypes(): Set<String> = registry.keys
}
