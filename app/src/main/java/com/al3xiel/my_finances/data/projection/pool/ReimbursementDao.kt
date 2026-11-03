package com.al3xiel.my_finances.data.projection.pool

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.al3xiel.my_finances.feature.pools.domain.rules.ReimbursementStatus

/** Escritura para el proyector de reembolsos; las consultas de pantalla vienen en T15. */
@Dao
interface ReimbursementDao {
    @Insert suspend fun insertReimbursement(reimbursement: ReimbursementEntity)

    @Insert suspend fun insertAllocations(allocations: List<ReimbursementAllocationEntity>)

    @Query("SELECT * FROM reimbursements WHERE movement_id = :movementId") suspend fun byMovement(movementId: String): ReimbursementEntity?

    @Query("SELECT part_id FROM reimbursement_allocations WHERE reimbursement_id = :reimbursementId")
    suspend fun partIdsOf(reimbursementId: String): List<String>

    @Query("DELETE FROM reimbursement_allocations WHERE reimbursement_id = :reimbursementId")
    suspend fun deleteAllocationsOf(reimbursementId: String)

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM reimbursement_allocations WHERE part_id = :partId")
    suspend fun allocatedTo(partId: String): Long

    @Query("SELECT amount_minor FROM movement_parts WHERE id = :partId") suspend fun partAmount(partId: String): Long?

    @Query("UPDATE movement_parts SET reimbursed_minor = :reimbursedMinor, reimbursement_status = :status WHERE id = :partId")
    suspend fun setPartReimbursement(partId: String, reimbursedMinor: Long, status: ReimbursementStatus)

    @Query("UPDATE movement_parts SET reimbursed_minor = 0, reimbursement_status = 'PENDING'") suspend fun resetParts()
    @Query("DELETE FROM reimbursement_allocations") suspend fun clearAllocations()
    @Query("DELETE FROM reimbursements") suspend fun clearReimbursements()
    @Query("DELETE FROM movements WHERE type = 'REIMBURSEMENT'") suspend fun deleteOwnMovements()
    @Query("DELETE FROM ledger_entries WHERE entry_kind = 'REIMBURSEMENT_IN'") suspend fun deleteOwnEntries()
}
