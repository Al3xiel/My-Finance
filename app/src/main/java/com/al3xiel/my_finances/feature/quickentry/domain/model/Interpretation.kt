package com.al3xiel.my_finances.feature.quickentry.domain.model

data class Candidate(val kind: EntityKind, val id: String, val label: String)

/** Un token que coincide con más de una entidad: no se adivina, el usuario elige. */
data class Ambiguity(val text: String, val candidates: List<Candidate>)

enum class Conflict { ACCOUNTS, CATEGORIES, POOL_AND_FUND, POOLS, FUNDS, SOURCES }

/** Lo entendido de una línea. Los ids son textos de las entidades del catálogo. */
data class Interpretation(
    val isIncome: Boolean,
    val amountMinor: Long?,
    val accountIds: List<String>,
    val categoryIds: List<String>,
    val tagIds: List<String>,
    val poolIds: List<String>,
    val fundIds: List<String>,
    val sourceIds: List<String>,
    val ambiguities: List<Ambiguity>,
    val note: String?,
) {
    val conflicts: Set<Conflict> = buildSet {
        if (accountIds.size > 1) add(Conflict.ACCOUNTS)
        if (categoryIds.size > 1) add(Conflict.CATEGORIES)
        if (poolIds.isNotEmpty() && fundIds.isNotEmpty()) add(Conflict.POOL_AND_FUND)
        if (poolIds.size > 1) add(Conflict.POOLS)
        if (fundIds.size > 1) add(Conflict.FUNDS)
        if (sourceIds.size > 1) add(Conflict.SOURCES)
    }
}
