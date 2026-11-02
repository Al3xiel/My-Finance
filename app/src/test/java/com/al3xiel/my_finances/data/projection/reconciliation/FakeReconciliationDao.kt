package com.al3xiel.my_finances.data.projection.reconciliation

import com.al3xiel.my_finances.data.projection.movement.FakeLedgerDao
import com.al3xiel.my_finances.data.projection.movement.FakeMovementDao
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus

/** En memoria; `computedMinor` repite la semántica de la consulta SQL sobre los asientos del fake (D30). */
class FakeReconciliationDao(
    private val movementDao: FakeMovementDao,
    private val ledgerDao: FakeLedgerDao,
) : ReconciliationDao {
    val reconciliations = linkedMapOf<String, ReconciliationEntity>()
    val links = linkedSetOf<ReconciliationLinkEntity>()

    override suspend fun upsert(reconciliation: ReconciliationEntity) { reconciliations[reconciliation.id] = reconciliation }
    override suspend fun reconciliation(id: String) = reconciliations[id]
    override suspend fun resolved() = reconciliations.values.filter { it.status == ReconciliationStatus.RESOLVED }
    override suspend fun byAdjustment(movementId: String) = reconciliations.values.firstOrNull { it.adjustmentMovementId == movementId }
    override suspend fun insertLink(link: ReconciliationLinkEntity) { links += link }

    override suspend fun computedMinor(reconciliationId: String, accountId: String, checkedSequence: Long): Long {
        val linked = links.filter { it.reconciliationId == reconciliationId }.map { it.movementId }.toSet()
        val entries = ledgerDao.entries.values.filter { it.accountId == accountId }
        val atCheck = entries.filter {
            it.sequence <= checkedSequence && (!it.isVoided || (it.voidedSequence ?: 0L) > checkedSequence) && it.movementId !in linked
        }.sumOf { it.deltaMinor }
        val current = entries.filter { !it.isVoided && it.movementId in linked }.sumOf { it.deltaMinor }
        return atCheck + current
    }

    override suspend fun isMovementVoided(movementId: String) = movementDao.movements[movementId]?.status == MovementStatus.VOIDED

    override suspend fun clearReconciliations() = reconciliations.clear()
    override suspend fun clearLinks() = links.clear()

    override suspend fun deleteOwnMovements() {
        movementDao.movements.values.removeAll { it.type == MovementType.ADJUSTMENT }
    }

    override suspend fun deleteOwnEntries() {
        ledgerDao.entries.values.removeAll { it.entryKind == LedgerEntryKind.ADJUSTMENT }
    }
}
