package com.al3xiel.my_finances.feature.reports.domain.rules

import com.al3xiel.my_finances.core.money.LargestRemainder
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRow
import com.al3xiel.my_finances.feature.reports.domain.model.OriginEntry
import com.al3xiel.my_finances.feature.reports.domain.model.OriginSourceIds

/**
 * Motor de origen del dinero (docs/04 §5.1): mantiene cuánto del saldo de cada cuenta viene de cada fuente. Kotlin
 * puro y determinista; los asientos se aplican en el orden de [ORDER]. Invariante: Σ de la mezcla de una cuenta = Σ de
 * sus asientos aplicados.
 */
class OriginEngine(initial: Map<Pair<String, String>, Long> = emptyMap()) {
    private val mix = HashMap<String, MutableMap<String, Long>>()
    private val pendingTransfers = HashMap<String, Map<String, Long>>()

    init {
        initial.forEach { (key, amount) -> add(key.first, key.second, amount) }
    }

    /** Aplica un asiento y devuelve sus atribuciones (vacío para ingresos y transferencias). */
    fun apply(entry: OriginEntry): List<OriginAttributionRow> {
        val delta = entry.deltaMinor
        return when {
            entry.kind == LedgerEntryKind.TRANSFER_OUT -> {
                pendingTransfers[entry.movementId ?: entry.id] = deduct(entry.accountId, -delta)
                emptyList()
            }
            entry.kind == LedgerEntryKind.TRANSFER_IN -> {
                val carried = pendingTransfers.remove(entry.movementId ?: entry.id)
                if (carried == null) add(entry.accountId, OriginSourceIds.UNKNOWN_ADJUSTMENT, delta)
                else carried.forEach { (source, amount) -> add(entry.accountId, source, amount) }
                emptyList()
            }
            delta >= 0 -> {
                add(entry.accountId, positiveSource(entry), delta)
                emptyList()
            }
            else -> attributeOutflow(entry, -delta)
        }
    }

    /** Mezcla actual por (cuenta, fuente), sin filas en cero. */
    fun balances(): Map<Pair<String, String>, Long> = buildMap {
        mix.forEach { (account, sources) -> sources.forEach { (s, v) -> if (v != 0L) put(account to s, v) } }
    }

    private fun positiveSource(entry: OriginEntry): String = when (entry.kind) {
        LedgerEntryKind.OPENING -> OriginSourceIds.OPENING_BALANCE
        LedgerEntryKind.ADJUSTMENT -> OriginSourceIds.UNKNOWN_ADJUSTMENT
        else -> entry.sourceId ?: OriginSourceIds.UNKNOWN_ADJUSTMENT
    }

    private fun attributeOutflow(entry: OriginEntry, amount: Long): List<OriginAttributionRow> =
        partsOf(entry, amount).flatMap { (partId, partAmount) ->
            deduct(entry.accountId, partAmount).map { (source, v) -> OriginAttributionRow(entry.id, partId, source, v) }
        }

    /** Las partes en su orden; lo que no cubren va sin parte y lo que sobra se recorta (los asientos son la verdad). */
    private fun partsOf(entry: OriginEntry, amount: Long): List<Pair<String, Long>> {
        var left = amount
        val out = mutableListOf<Pair<String, Long>>()
        for (p in entry.parts) {
            val take = p.amountMinor.coerceIn(0, left)
            if (take > 0) out += p.partId to take
            left -= take
        }
        if (left > 0) out += "" to left
        return out
    }

    /**
     * Descuenta `amount` en proporción de las fuentes con saldo positivo (mayor residuo, desempate por id); lo que
     * no alcanza va a `UNKNOWN_ADJUSTMENT`, que puede quedar negativa. Devuelve lo descontado por fuente.
     */
    private fun deduct(account: String, amount: Long): Map<String, Long> {
        if (amount <= 0) return emptyMap()
        val sources = mix.getOrPut(account) { HashMap() }
        val positives = sources.filterValues { it > 0 }.toList().sortedBy { it.first }
        val taken = minOf(amount, positives.sumOf { it.second })
        val result = sortedMapOf<String, Long>()
        if (taken > 0) {
            LargestRemainder.distribute(taken, positives).forEach { (source, share) ->
                if (share > 0) {
                    result[source] = share
                    sources[source] = sources.getValue(source) - share
                }
            }
        }
        val shortfall = amount - taken
        if (shortfall > 0) {
            val unknown = OriginSourceIds.UNKNOWN_ADJUSTMENT
            result[unknown] = (result[unknown] ?: 0L) + shortfall
            sources[unknown] = (sources[unknown] ?: 0L) - shortfall
        }
        return result
    }

    private fun add(account: String, source: String, amount: Long) {
        val sources = mix.getOrPut(account) { HashMap() }
        sources[source] = (sources[source] ?: 0L) + amount
    }

    companion object {
        /** Clave de orden (D40); a igualdad, la salida de una transferencia antes que su entrada. */
        val ORDER: Comparator<OriginEntry> = compareBy<OriginEntry> { it.orderKey }
            .thenBy { it.sequence }
            .thenBy { if (it.kind == LedgerEntryKind.TRANSFER_IN) 1 else 0 }
            .thenBy { it.id }
    }
}
