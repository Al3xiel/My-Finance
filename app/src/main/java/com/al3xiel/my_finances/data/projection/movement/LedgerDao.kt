package com.al3xiel.my_finances.data.projection.movement

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LedgerDao {
    @Insert suspend fun insertEntries(entries: List<LedgerEntryEntity>)

    @Query("SELECT DISTINCT account_id FROM ledger_entries WHERE movement_id = :movementId")
    suspend fun accountIdsOf(movementId: String): List<String>

    @Query("UPDATE ledger_entries SET is_voided = 1 WHERE movement_id = :movementId")
    suspend fun voidEntriesOf(movementId: String)

    /** Saldo = Σ de los asientos no anulados (docs/04 §4.3). */
    @Query("SELECT COALESCE(SUM(delta_minor), 0) FROM ledger_entries WHERE account_id = :accountId AND is_voided = 0")
    suspend fun balanceOf(accountId: String): Long

    @Query("SELECT last_sequence FROM account_balances WHERE account_id = :accountId")
    suspend fun lastSequenceOf(accountId: String): Long?

    @Upsert suspend fun upsertBalance(balance: AccountBalanceEntity)

    @Query("DELETE FROM ledger_entries") suspend fun clearEntries()
    @Query("DELETE FROM account_balances") suspend fun clearBalances()
}
