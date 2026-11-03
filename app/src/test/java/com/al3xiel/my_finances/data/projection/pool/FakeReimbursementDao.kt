package com.al3xiel.my_finances.data.projection.pool

import com.al3xiel.my_finances.data.projection.movement.FakeLedgerDao
import com.al3xiel.my_finances.data.projection.movement.FakeMovementDao
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.feature.pools.domain.rules.ReimbursementStatus

/** DAO en memoria; comparte partes, movimientos y asientos con los fakes de los otros proyectores. */
class FakeReimbursementDao(
    private val movementDao: FakeMovementDao,
    private val ledgerDao: FakeLedgerDao,
) : ReimbursementDao {
    val reimbursements = linkedMapOf<String, ReimbursementEntity>()
    val allocations = linkedSetOf<ReimbursementAllocationEntity>()

    override suspend fun insertReimbursement(reimbursement: ReimbursementEntity) { reimbursements[reimbursement.id] = reimbursement }
    override suspend fun insertAllocations(allocations: List<ReimbursementAllocationEntity>) { this.allocations += allocations }
    override suspend fun byMovement(movementId: String) = reimbursements.values.firstOrNull { it.movementId == movementId }

    override suspend fun partIdsOf(reimbursementId: String) =
        allocations.filter { it.reimbursementId == reimbursementId }.map { it.partId }

    override suspend fun deleteAllocationsOf(reimbursementId: String) {
        allocations.removeAll { it.reimbursementId == reimbursementId }
    }

    override suspend fun allocatedTo(partId: String) = allocations.filter { it.partId == partId }.sumOf { it.amountMinor }
    override suspend fun partAmount(partId: String) = movementDao.parts[partId]?.amountMinor

    override suspend fun setPartReimbursement(partId: String, reimbursedMinor: Long, status: ReimbursementStatus) {
        movementDao.parts[partId]?.let {
            movementDao.parts[partId] = it.copy(reimbursedMinor = reimbursedMinor, reimbursementStatus = status)
        }
    }

    override suspend fun resetParts() {
        movementDao.parts.replaceAll { _, p -> p.copy(reimbursedMinor = 0, reimbursementStatus = ReimbursementStatus.PENDING) }
    }

    override suspend fun clearAllocations() = allocations.clear()
    override suspend fun clearReimbursements() = reimbursements.clear()
    override suspend fun deleteOwnMovements() { movementDao.movements.values.removeAll { it.type == MovementType.REIMBURSEMENT } }
    override suspend fun deleteOwnEntries() { ledgerDao.entries.values.removeAll { it.entryKind == LedgerEntryKind.REIMBURSEMENT_IN } }
}
