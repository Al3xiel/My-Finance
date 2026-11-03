package com.al3xiel.my_finances.data.projection.origin

import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRow
import com.al3xiel.my_finances.feature.reports.domain.model.OriginEntry
import com.al3xiel.my_finances.feature.reports.domain.model.OriginPart
import com.al3xiel.my_finances.feature.reports.domain.rules.OriginEngine
import javax.inject.Inject

/**
 * Trazabilidad de origen (docs/04 §5.1). Corre después de movimientos, reembolsos, deudas y conciliación (`order`): no
 * lee eventos, solo el libro que esos proyectores dejan. El resultado depende del orden de todos los asientos, así que:
 * - en vivo, si los asientos del evento son los últimos por la clave de orden, se aplican sobre el estado guardado
 *   (`origin_cursor`); si el evento anuló o corrigió algo, o llega un asiento anterior, se recalcula todo (D40);
 * - en el replay `apply` no hace nada y `finishReplay` recalcula una sola vez, porque el libro ya está final.
 */
class OriginProjector @Inject constructor(private val dao: OriginDao) : EventProjector {
    override val name = "origin"
    override val version = 1
    override val order = 40

    /** Solo el reconstructor la activa (`beginReplay`/`finishReplay`/`abortReplay`); no es segura entre hilos ni entre instancias. */
    private var replaying = false

    override suspend fun apply(event: StoredEvent) {
        if (replaying) return
        val voided = dao.voidedAt(event.sequence) > 0
        val fresh = dao.entriesAt(event.sequence)
        if (!voided && fresh.isEmpty()) return
        if (voided || !isAfterCursor(fresh)) recomputeAll() else applyFast(fresh)
    }

    override suspend fun reset() {
        dao.clearAttributions()
        dao.clearBalances()
        dao.clearCursor()
    }

    override suspend fun beginReplay() { replaying = true }

    override suspend fun finishReplay() {
        recomputeAll()
        replaying = false
    }

    override suspend fun abortReplay() { replaying = false }

    private suspend fun isAfterCursor(fresh: List<OriginEntryRow>): Boolean {
        val cursor = dao.cursor() ?: return true
        return fresh.all {
            it.orderKey > cursor.lastOrderKey || (it.orderKey == cursor.lastOrderKey && it.sequence > cursor.lastSequence)
        }
    }

    private suspend fun applyFast(fresh: List<OriginEntryRow>) {
        val before = dao.balances().associate { (it.accountId to it.incomeSourceId) to it.amountMinor }
        val parts = fresh.mapNotNull { it.movementId }.distinct().associateWith { dao.partsOf(it) }
        val engine = OriginEngine(before)
        val entries = fresh.map { it.toEntry(parts[it.movementId].orEmpty()) }.sortedWith(OriginEngine.ORDER)
        val attributions = entries.flatMap(engine::apply)
        val after = engine.balances()
        dao.upsertBalances(after.filter { (k, v) -> before[k] != v }.map { (k, v) -> OriginBalanceEntity(k.first, k.second, v) })
        (before.keys - after.keys).forEach { dao.deleteBalance(it.first, it.second) }
        dao.insertAttributions(attributions.map { it.toEntity() })
        entries.last().let { dao.upsertCursor(OriginCursorEntity(1, it.orderKey, it.sequence, it.id)) }
    }

    private suspend fun recomputeAll() {
        reset()
        val parts = dao.parts().groupBy { it.movementId }
        val entries = dao.entries().map { it.toEntry(parts[it.movementId].orEmpty()) }.sortedWith(OriginEngine.ORDER)
        val engine = OriginEngine()
        val attributions = entries.flatMap(engine::apply)
        dao.upsertBalances(engine.balances().map { (k, v) -> OriginBalanceEntity(k.first, k.second, v) })
        dao.insertAttributions(attributions.map { it.toEntity() })
        entries.lastOrNull()?.let { dao.upsertCursor(OriginCursorEntity(1, it.orderKey, it.sequence, it.id)) }
    }

    private fun OriginEntryRow.toEntry(parts: List<OriginPartRow>) = OriginEntry(
        id = id, sequence = sequence, orderKey = orderKey, accountId = accountId, deltaMinor = deltaMinor, kind = entryKind,
        movementId = movementId, sourceId = sourceId,
        parts = if (entryKind == LedgerEntryKind.EXPENSE || entryKind == LedgerEntryKind.FEE) {
            parts.map { OriginPart(it.id, it.amountMinor) }
        } else {
            emptyList()
        },
    )

    private fun OriginAttributionRow.toEntity() = OriginAttributionEntity(entryId, partId, sourceId, amountMinor)
}
