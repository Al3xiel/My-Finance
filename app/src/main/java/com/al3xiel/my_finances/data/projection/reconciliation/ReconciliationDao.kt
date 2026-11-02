package com.al3xiel.my_finances.data.projection.reconciliation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

/**
 * Saldo de la app al momento (D30): asientos de la cuenta con `sequence` ≤ el chequeo y no anulados antes de él, sin los
 * de movimientos vinculados, más los asientos vigentes de los vinculados.
 */
internal const val COMPUTED_BALANCE_SQL =
    "SELECT COALESCE((SELECT SUM(delta_minor) FROM ledger_entries WHERE account_id = :accountId AND sequence <= :checkedSequence " +
    "AND (is_voided = 0 OR voided_sequence > :checkedSequence) " +
    "AND (movement_id IS NULL OR movement_id NOT IN (SELECT movement_id FROM reconciliation_links WHERE reconciliation_id = :reconciliationId))), 0) " +
    "+ COALESCE((SELECT SUM(delta_minor) FROM ledger_entries WHERE account_id = :accountId AND is_voided = 0 " +
    "AND movement_id IN (SELECT movement_id FROM reconciliation_links WHERE reconciliation_id = :reconciliationId)), 0)"

/** Escritura para el proyector de conciliaciones; las consultas de pantalla viven en `ReconciliationQueryDao`. */
@Dao
interface ReconciliationDao {
    @Upsert suspend fun upsert(reconciliation: ReconciliationEntity)

    @Query("SELECT * FROM reconciliations WHERE id = :id") suspend fun reconciliation(id: String): ReconciliationEntity?

    @Query("SELECT * FROM reconciliations WHERE status = 'RESOLVED' ORDER BY checked_sequence")
    suspend fun resolved(): List<ReconciliationEntity>

    @Query("SELECT * FROM reconciliations WHERE adjustment_movement_id = :movementId")
    suspend fun byAdjustment(movementId: String): ReconciliationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLink(link: ReconciliationLinkEntity)

    @Query(COMPUTED_BALANCE_SQL)
    suspend fun computedMinor(reconciliationId: String, accountId: String, checkedSequence: Long): Long

    @Query("SELECT COUNT(*) > 0 FROM movements WHERE id = :movementId AND status = 'VOIDED'")
    suspend fun isMovementVoided(movementId: String): Boolean

    @Query("DELETE FROM reconciliations") suspend fun clearReconciliations()
    @Query("DELETE FROM reconciliation_links") suspend fun clearLinks()
    @Query("DELETE FROM movements WHERE type = 'ADJUSTMENT'") suspend fun deleteOwnMovements()
    @Query("DELETE FROM ledger_entries WHERE entry_kind = 'ADJUSTMENT'") suspend fun deleteOwnEntries()
}
