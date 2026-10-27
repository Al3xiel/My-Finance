package com.al3xiel.my_finances.data.projection.movement

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerQueryDao {
    @Query("SELECT * FROM account_balances") fun observeBalances(): Flow<List<AccountBalanceEntity>>

    @Query("SELECT balance_minor FROM account_balances WHERE account_id = :accountId")
    fun observeBalance(accountId: String): Flow<Long?>

    @Query("SELECT balance_minor FROM account_balances WHERE account_id = :accountId")
    suspend fun balance(accountId: String): Long?

    /** Saldo a una fecha (docs/04 §4.3). */
    @Query(
        "SELECT COALESCE(SUM(delta_minor), 0) FROM ledger_entries " +
            "WHERE account_id = :accountId AND occurred_on <= :asOf AND is_voided = 0",
    )
    suspend fun balanceAt(accountId: String, asOf: String): Long

    @Query("SELECT * FROM ledger_entries WHERE account_id = :accountId ORDER BY occurred_on DESC, sequence DESC, id DESC")
    fun observeEntries(accountId: String): Flow<List<LedgerEntryEntity>>
}
