package com.al3xiel.my_finances.data.projection.reconciliation

import androidx.room.Dao
import androidx.room.Query
import com.al3xiel.my_finances.data.projection.movement.MovementEntity
import kotlinx.coroutines.flow.Flow

/** Lecturas de conciliaciones para el repositorio y las pantallas; el proyector no las usa. */
@Dao
interface ReconciliationQueryDao {
    @Query("SELECT * FROM reconciliations WHERE id = :id") suspend fun reconciliation(id: String): ReconciliationEntity?

    @Query("SELECT * FROM reconciliations WHERE id = :id") fun observeReconciliation(id: String): Flow<ReconciliationEntity?>

    @Query("SELECT * FROM reconciliations WHERE account_id = :accountId AND status = 'OPEN' LIMIT 1")
    suspend fun openFor(accountId: String): ReconciliationEntity?

    @Query("SELECT * FROM reconciliations WHERE account_id = :accountId ORDER BY checked_sequence DESC LIMIT 1")
    fun observeLatest(accountId: String): Flow<ReconciliationEntity?>

    @Query("SELECT COUNT(*) FROM reconciliations WHERE needs_review = 1") fun observeNeedsReviewCount(): Flow<Int>

    /** La conciliación resuelta que pide revisión y lleva más tiempo así (alerta de Inicio, D56). */
    @Query("SELECT id FROM reconciliations WHERE needs_review = 1 ORDER BY checked_sequence, id LIMIT 1")
    fun observeOldestNeedsReviewId(): Flow<String?>

    /** La conciliación `OPEN` más antigua, de cualquier cuenta (alerta de Inicio, D56). */
    @Query("SELECT id FROM reconciliations WHERE status = 'OPEN' ORDER BY checked_sequence, id LIMIT 1")
    fun observeOpenId(): Flow<String?>

    @Query("SELECT COUNT(*) > 0 FROM reconciliation_links WHERE reconciliation_id = :reconciliationId AND movement_id = :movementId")
    suspend fun isLinked(reconciliationId: String, movementId: String): Boolean

    @Query(COMPUTED_BALANCE_SQL)
    suspend fun computedMinor(reconciliationId: String, accountId: String, checkedSequence: Long): Long

    @Query(COMPUTED_BALANCE_SQL)
    fun observeComputedMinor(reconciliationId: String, accountId: String, checkedSequence: Long): Flow<Long>

    @Query(
        """
        SELECT m.* FROM movements m
        JOIN reconciliation_links l ON l.movement_id = m.id
        WHERE l.reconciliation_id = :reconciliationId
        ORDER BY m.last_sequence, m.id
        """,
    )
    fun observeLinked(reconciliationId: String): Flow<List<MovementEntity>>

    /**
     * Movimientos activos de la cuenta que dejaron un asiento vigente después del chequeo y aún no están vinculados:
     * los candidatos a explicar la diferencia. Los ajustes de conciliación no se sugieren.
     */
    @Query(
        """
        SELECT m.* FROM movements m
        JOIN reconciliations r ON r.id = :reconciliationId
        WHERE m.status = 'ACTIVE' AND m.type <> 'ADJUSTMENT'
          AND (m.account_id = r.account_id OR m.counter_account_id = r.account_id)
          AND m.id NOT IN (SELECT movement_id FROM reconciliation_links WHERE reconciliation_id = r.id)
          AND EXISTS (
                SELECT 1 FROM ledger_entries e
                WHERE e.movement_id = m.id AND e.account_id = r.account_id AND e.is_voided = 0 AND e.sequence > r.checked_sequence)
        ORDER BY m.last_sequence DESC, m.id
        """,
    )
    fun observeSuggestions(reconciliationId: String): Flow<List<MovementEntity>>
}
