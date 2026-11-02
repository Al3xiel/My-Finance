package com.al3xiel.my_finances.data.projection.origin

import com.al3xiel.my_finances.data.projection.movement.FakeLedgerDao
import com.al3xiel.my_finances.data.projection.movement.FakeMovementDao

/** En memoria: repite la semántica de las consultas SQL de `OriginDao` sobre los fakes de movimientos y libro. */
class FakeOriginDao(
    private val movementDao: FakeMovementDao,
    private val ledgerDao: FakeLedgerDao,
) : OriginDao {
    val balanceRows = linkedMapOf<Pair<String, String>, OriginBalanceEntity>()
    val attributionRows = linkedMapOf<Triple<String, String, String>, OriginAttributionEntity>()
    var cursorRow: OriginCursorEntity? = null

    /** Cuántas veces se leyó el libro entero: el camino rápido no debe hacerlo. */
    var fullReads = 0

    private fun rows(onlySequence: Long?): List<OriginEntryRow> {
        val all = ledgerDao.entries.values
        return all.filter { !it.isVoided && (onlySequence == null || it.sequence == onlySequence) }.map { e ->
            val orderKey = e.movementId?.let { id -> all.filter { it.movementId == id }.minOf { it.sequence } } ?: e.sequence
            OriginEntryRow(
                e.id, e.sequence, e.movementId, e.accountId, e.deltaMinor, e.entryKind,
                e.movementId?.let { movementDao.movements[it]?.incomeSourceId }, orderKey,
            )
        }
    }

    override suspend fun entries(): List<OriginEntryRow> { fullReads++; return rows(null) }
    override suspend fun entriesAt(sequence: Long) = rows(sequence)
    override suspend fun voidedAt(sequence: Long) = ledgerDao.entries.values.count { it.voidedSequence == sequence }

    override suspend fun parts() = movementDao.parts.values.map { OriginPartRow(it.id, it.movementId, it.amountMinor) }
    override suspend fun partsOf(movementId: String) = parts().filter { it.movementId == movementId }

    override suspend fun balances() = balanceRows.values.toList()
    override suspend fun cursor() = cursorRow

    override suspend fun upsertBalances(rows: List<OriginBalanceEntity>) {
        rows.forEach { balanceRows[it.accountId to it.incomeSourceId] = it }
    }

    override suspend fun deleteBalance(accountId: String, incomeSourceId: String) {
        balanceRows.remove(accountId to incomeSourceId)
    }

    override suspend fun insertAttributions(rows: List<OriginAttributionEntity>) {
        rows.forEach { attributionRows[Triple(it.ledgerEntryId, it.partId, it.incomeSourceId)] = it }
    }

    override suspend fun upsertCursor(cursor: OriginCursorEntity) { cursorRow = cursor }

    override suspend fun clearBalances() = balanceRows.clear()
    override suspend fun clearAttributions() = attributionRows.clear()
    override suspend fun clearCursor() { cursorRow = null }
}
