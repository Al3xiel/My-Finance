package com.al3xiel.my_finances.data.projection.origin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

/** Lectura del libro y escritura de `origin_*` para el proyector (las consultas de pantalla van en `OriginQueryDao`). */
@Dao
interface OriginDao {
    /** Asientos vigentes; la clave de orden es el menor `sequence` de su movimiento, anulados incluidos (D40). */
    @Query(
        "SELECT l.id AS id, l.sequence AS sequence, l.movement_id AS movement_id, l.account_id AS account_id, " +
            "l.delta_minor AS delta_minor, l.entry_kind AS entry_kind, m.income_source_id AS source_id, " +
            "COALESCE((SELECT MIN(l2.sequence) FROM ledger_entries l2 WHERE l2.movement_id = l.movement_id), l.sequence) AS order_key " +
            "FROM ledger_entries l LEFT JOIN movements m ON m.id = l.movement_id WHERE l.is_voided = 0",
    )
    suspend fun entries(): List<OriginEntryRow>

    @Query(
        "SELECT l.id AS id, l.sequence AS sequence, l.movement_id AS movement_id, l.account_id AS account_id, " +
            "l.delta_minor AS delta_minor, l.entry_kind AS entry_kind, m.income_source_id AS source_id, " +
            "COALESCE((SELECT MIN(l2.sequence) FROM ledger_entries l2 WHERE l2.movement_id = l.movement_id), l.sequence) AS order_key " +
            "FROM ledger_entries l LEFT JOIN movements m ON m.id = l.movement_id WHERE l.is_voided = 0 AND l.sequence = :sequence",
    )
    suspend fun entriesAt(sequence: Long): List<OriginEntryRow>

    @Query("SELECT COUNT(*) FROM ledger_entries WHERE voided_sequence = :sequence")
    suspend fun voidedAt(sequence: Long): Int

    /** En orden de registro: primero la secuencia en que nació la parte, luego su fila. */
    @Query("SELECT id, movement_id, amount_minor FROM movement_parts ORDER BY created_sequence, rowid")
    suspend fun parts(): List<OriginPartRow>

    @Query("SELECT id, movement_id, amount_minor FROM movement_parts WHERE movement_id = :movementId ORDER BY created_sequence, rowid")
    suspend fun partsOf(movementId: String): List<OriginPartRow>

    @Query("SELECT * FROM origin_balances") suspend fun balances(): List<OriginBalanceEntity>
    @Query("SELECT * FROM origin_cursor WHERE id = 1") suspend fun cursor(): OriginCursorEntity?

    @Upsert suspend fun upsertBalances(rows: List<OriginBalanceEntity>)
    @Query("DELETE FROM origin_balances WHERE account_id = :accountId AND income_source_id = :incomeSourceId")
    suspend fun deleteBalance(accountId: String, incomeSourceId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAttributions(rows: List<OriginAttributionEntity>)
    @Upsert suspend fun upsertCursor(cursor: OriginCursorEntity)

    @Query("DELETE FROM origin_balances") suspend fun clearBalances()
    @Query("DELETE FROM origin_attributions") suspend fun clearAttributions()
    @Query("DELETE FROM origin_cursor") suspend fun clearCursor()
}
