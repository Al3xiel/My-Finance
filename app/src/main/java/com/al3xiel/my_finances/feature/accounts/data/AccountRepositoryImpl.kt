package com.al3xiel.my_finances.feature.accounts.data

import com.al3xiel.my_finances.data.projection.catalog.AccountEntity
import com.al3xiel.my_finances.data.projection.catalog.CatalogDao
import com.al3xiel.my_finances.data.projection.catalog.CatalogQueryDao
import com.al3xiel.my_finances.data.projection.movement.LedgerEntryEntity
import com.al3xiel.my_finances.data.projection.movement.LedgerQueryDao
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.accounts.domain.model.LedgerLine
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val catalog: CatalogDao,
    private val catalogQueries: CatalogQueryDao,
    private val ledger: LedgerQueryDao,
) : AccountRepository {
    override suspend fun find(id: AccountId): Account? = catalog.account(id.value)?.toDomain()

    override suspend fun activeNames(): List<String> = catalogQueries.activeAccountNames()

    override suspend fun balanceMinor(id: AccountId): Long = ledger.balance(id.value) ?: 0L

    override suspend fun balanceAt(id: AccountId, asOf: LocalDate): Long = ledger.balanceAt(id.value, asOf.toString())

    override fun observeAccounts(): Flow<List<AccountWithBalance>> =
        combine(catalogQueries.observeAccounts(), ledger.observeBalances()) { accounts, balances ->
            val byAccount = balances.associate { it.accountId to it.balanceMinor }
            accounts.map { AccountWithBalance(it.toDomain(), byAccount[it.id] ?: 0L) }
        }

    override fun observeAccount(id: AccountId): Flow<AccountWithBalance?> =
        combine(catalogQueries.observeAccount(id.value), ledger.observeBalance(id.value)) { account, balance ->
            account?.let { AccountWithBalance(it.toDomain(), balance ?: 0L) }
        }

    override fun observeLedger(id: AccountId): Flow<List<LedgerLine>> =
        ledger.observeEntries(id.value).map { entries -> entries.map { it.toDomain() } }
}

private fun AccountEntity.toDomain() = Account(AccountId(id), name, type, currency, color, isArchived)

private fun LedgerEntryEntity.toDomain() = LedgerLine(
    id = id,
    sequence = sequence,
    movementId = movementId?.let(::MovementId),
    deltaMinor = deltaMinor,
    kind = entryKind,
    occurredOn = LocalDate.parse(occurredOn),
    isVoided = isVoided,
)
