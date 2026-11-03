package com.al3xiel.my_finances.feature.quickentry.domain.rules

import com.al3xiel.my_finances.feature.quickentry.domain.model.Ambiguity
import com.al3xiel.my_finances.feature.quickentry.domain.model.Candidate
import com.al3xiel.my_finances.feature.quickentry.domain.model.CatalogEntry
import com.al3xiel.my_finances.feature.quickentry.domain.model.CatalogSnapshot
import com.al3xiel.my_finances.feature.quickentry.domain.model.EntityKind
import com.al3xiel.my_finances.feature.quickentry.domain.model.Interpretation

/**
 * Entiende una línea como «45 bbva mercado perros» o «+50 bcp sueldo». Puro y determinista.
 * Un gasto nombra cuenta, categoría, concepto, cúmulo y fondo; un ingreso (`+`) solo cuenta y fuente.
 * Exacta antes que prefijo (≥ 3 letras); la secuencia de palabras más larga primero (D63).
 */
object QuickEntryInterpreter {
    private const val MIN_PREFIX = 3
    private const val MAX_WORDS = 4
    private val expenseKinds = setOf(EntityKind.ACCOUNT, EntityKind.CATEGORY, EntityKind.TAG, EntityKind.POOL, EntityKind.FUND)
    private val incomeKinds = setOf(EntityKind.ACCOUNT, EntityKind.INCOME_SOURCE)
    private val spaces = Regex("\\s+")

    fun interpret(line: String, snapshot: CatalogSnapshot): Interpretation {
        val words = line.trim().split(spaces).filter { it.isNotEmpty() }
        val isIncome = words.firstOrNull()?.startsWith("+") == true
        val kinds = if (isIncome) incomeKinds else expenseKinds
        val entries = snapshot.entries.filter { it.kind in kinds }
        val normalized = words.map { TokenNormalizer.normalize(it) }

        var amount: Long? = null
        val matched = LinkedHashMap<EntityKind, MutableList<String>>()
        val ambiguities = mutableListOf<Ambiguity>()
        val note = mutableListOf<String>()

        var i = 0
        while (i < words.size) {
            val word = words[i]
            if (amount == null) {
                val own = AmountParser.parseMinor(word)
                val joined = if (own == null && AmountParser.isCurrencyPrefix(word) && i + 1 < words.size) AmountParser.parseMinor(words[i + 1]) else null
                if (own != null) {
                    amount = own
                    i++
                    continue
                }
                if (joined != null) {
                    amount = joined
                    i += 2
                    continue
                }
            }
            val consumed = matchAt(i, normalized, entries, matched, ambiguities, words)
            if (consumed > 0) {
                i += consumed
            } else {
                note += word
                i++
            }
        }
        return Interpretation(
            isIncome = isIncome,
            amountMinor = amount,
            accountIds = matched[EntityKind.ACCOUNT].orEmpty(),
            categoryIds = matched[EntityKind.CATEGORY].orEmpty(),
            tagIds = matched[EntityKind.TAG].orEmpty(),
            poolIds = matched[EntityKind.POOL].orEmpty(),
            fundIds = matched[EntityKind.FUND].orEmpty(),
            sourceIds = matched[EntityKind.INCOME_SOURCE].orEmpty(),
            ambiguities = ambiguities,
            note = note.joinToString(" ").ifEmpty { null },
        )
    }

    /** Devuelve cuántas palabras consumió (0 = ninguna coincidencia). */
    private fun matchAt(
        start: Int,
        normalized: List<String>,
        entries: List<CatalogEntry>,
        matched: MutableMap<EntityKind, MutableList<String>>,
        ambiguities: MutableList<Ambiguity>,
        words: List<String>,
    ): Int {
        val longest = minOf(MAX_WORDS, normalized.size - start)
        for (length in longest downTo 1) {
            val text = normalized.subList(start, start + length).joinToString(" ").removePrefix("+")
            if (text.isEmpty()) continue
            val exact = entries.filter { entry -> entry.names.any { TokenNormalizer.normalize(it) == text } }
            if (exact.isNotEmpty()) {
                record(exact, words.subList(start, start + length).joinToString(" "), matched, ambiguities)
                return length
            }
        }
        val single = normalized[start].removePrefix("+")
        if (single.length >= MIN_PREFIX) {
            val prefixed = entries.filter { entry -> entry.names.any { TokenNormalizer.normalize(it).startsWith(single) } }
            if (prefixed.isNotEmpty()) {
                record(prefixed, words[start], matched, ambiguities)
                return 1
            }
        }
        return 0
    }

    private fun record(
        found: List<CatalogEntry>,
        text: String,
        matched: MutableMap<EntityKind, MutableList<String>>,
        ambiguities: MutableList<Ambiguity>,
    ) {
        val distinct = found.distinctBy { it.kind to it.id }
        if (distinct.size == 1) {
            val entry = distinct.single()
            val ids = matched.getOrPut(entry.kind) { mutableListOf() }
            if (entry.id !in ids) ids += entry.id
        } else {
            ambiguities += Ambiguity(text.removePrefix("+"), distinct.map { Candidate(it.kind, it.id, it.names.first()) })
        }
    }
}
