package com.al3xiel.my_finances.feature.quickentry.domain.usecase

import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.quickentry.domain.model.CatalogEntry
import com.al3xiel.my_finances.feature.quickentry.domain.model.CatalogSnapshot
import com.al3xiel.my_finances.feature.quickentry.domain.model.EntityKind
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Lo activo que el texto puede nombrar; un cúmulo también responde a su responsable. */
class LoadCatalogSnapshotUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
    private val pools: PoolRepository,
    private val people: PersonRepository,
    private val funds: FundRepository,
    private val sources: IncomeSourceRepository,
) {
    suspend operator fun invoke(): CatalogSnapshot {
        val aliases = people.observeAll().first().associate { it.id to it.alias }
        val entries = buildList {
            accounts.observeAccounts().first().filter { !it.account.isArchived }
                .forEach { add(CatalogEntry(EntityKind.ACCOUNT, it.account.id.value, listOf(it.account.name))) }
            categories.observeAll().first().filter { !it.isArchived }
                .forEach { add(CatalogEntry(EntityKind.CATEGORY, it.id.value, listOf(it.name))) }
            tags.observeAll().first().filter { !it.isArchived }
                .forEach { add(CatalogEntry(EntityKind.TAG, it.id.value, listOf(it.name))) }
            pools.observePools().first().filter { !it.isArchived }.forEach { pool ->
                add(CatalogEntry(EntityKind.POOL, pool.id.value, listOfNotNull(pool.name, aliases[pool.responsiblePersonId])))
            }
            funds.observeFunds().first().filter { it.fund.status == FundStatus.ACTIVE }
                .forEach { add(CatalogEntry(EntityKind.FUND, it.fund.id.value, listOf(it.fund.name))) }
            sources.observeAll().first()
                .forEach { add(CatalogEntry(EntityKind.INCOME_SOURCE, it.id.value, listOf(it.name))) }
        }
        return CatalogSnapshot(entries)
    }
}
