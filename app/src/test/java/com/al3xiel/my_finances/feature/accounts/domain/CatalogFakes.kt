package com.al3xiel.my_finances.feature.accounts.domain

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.accounts.domain.model.LedgerLine
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

class FakeAccountRepository : AccountRepository {
    val accounts = mutableMapOf<AccountId, Account>()
    val balances = mutableMapOf<AccountId, Long>()
    override suspend fun find(id: AccountId) = accounts[id]
    override suspend fun activeNames() = accounts.values.filter { !it.isArchived }.map { it.name }
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun balanceMinor(id: AccountId): Long {
        gate?.await()
        failure?.let { throw it }
        return balances[id] ?: 0L
    }
    val ledger = mutableMapOf<AccountId, List<LedgerLine>>()
    override suspend fun balanceAt(id: AccountId, asOf: LocalDate) =
        (ledger[id] ?: emptyList()).filter { !it.isVoided && !it.occurredOn.isAfter(asOf) }.sumOf { it.deltaMinor }
    override fun observeAccounts(): Flow<List<AccountWithBalance>> = flow {
        emit(accounts.values.sortedBy { it.isArchived }.map { AccountWithBalance(it, balances[it.id] ?: 0L) })
    }
    override fun observeAccount(id: AccountId): Flow<AccountWithBalance?> = flow {
        emit(accounts[id]?.let { AccountWithBalance(it, balances[id] ?: 0L) })
    }
    override fun observeLedger(id: AccountId): Flow<List<LedgerLine>> = flow { emit(ledger[id] ?: emptyList()) }
}

class FakeCategoryRepository : CategoryRepository {
    val categories = mutableMapOf<CategoryId, Category>()
    override suspend fun find(id: CategoryId) = categories[id]
    override suspend fun activeNames() = categories.values.filter { !it.isArchived }.map { it.name }
    override fun observeAll(): Flow<List<Category>> = flow { emit(categories.values.sortedBy { it.name }) }
}

class FakeTagRepository : TagRepository {
    val tags = mutableMapOf<TagId, Tag>()
    override suspend fun find(id: TagId) = tags[id]
    override suspend fun activeNames() = tags.values.filter { !it.isArchived }.map { it.name }
    override fun observeAll(): Flow<List<Tag>> = flow { emit(tags.values.sortedBy { it.name }) }
}

class FakePersonRepository : PersonRepository {
    val people = mutableMapOf<PersonId, Person>()
    override suspend fun find(id: PersonId) = people[id]
    override suspend fun activeAliases() = people.values.filter { !it.isArchived }.map { it.alias }
    override fun observeAll(): Flow<List<Person>> = flow { emit(people.values.sortedBy { it.alias }) }
}

class FakeIncomeSourceRepository : IncomeSourceRepository {
    val names = mutableListOf<String>()
    val sources = mutableMapOf<IncomeSourceId, IncomeSource>()
    override suspend fun find(id: IncomeSourceId) = sources[id]
    override suspend fun activeNames() = names + sources.values.map { it.name }
    override fun observeAll(): Flow<List<IncomeSource>> = flow { emit(sources.values.sortedBy { it.name }) }
}
