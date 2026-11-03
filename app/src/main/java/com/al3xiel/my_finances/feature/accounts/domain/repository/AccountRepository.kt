package com.al3xiel.my_finances.feature.accounts.domain.repository

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.accounts.domain.model.LedgerLine
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface AccountRepository {
    suspend fun find(id: AccountId): Account?

    /** Nombres de las cuentas no archivadas, tal como se guardaron. */
    suspend fun activeNames(): List<String>

    suspend fun balanceMinor(id: AccountId): Long

    /** Saldo contando solo los asientos hasta esa fecha del hecho, inclusive. */
    suspend fun balanceAt(id: AccountId, asOf: LocalDate): Long

    /** Activas primero, luego archivadas; cada una con su saldo. */
    fun observeAccounts(): Flow<List<AccountWithBalance>>

    fun observeAccount(id: AccountId): Flow<AccountWithBalance?>

    fun observeLedger(id: AccountId): Flow<List<LedgerLine>>
}
