package com.al3xiel.my_finances.feature.quickentry.domain.usecase

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import com.al3xiel.my_finances.feature.movements.domain.rules.PoolDefaults
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Lo que se propone cuando la línea no lo dice; la pantalla lo marca «sugerido» (D62). */
data class SuggestedDefaults(
    val accountId: AccountId?,
    val categoryId: CategoryId?,
    val tagIds: Set<TagId>,
)

class SuggestDefaultsUseCase @Inject constructor(
    private val movements: MovementRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val pools: PoolRepository,
) {
    /** Cuenta: la del último gasto si sigue activa, si no la primera activa. Categoría: la del cúmulo o la del último gasto. */
    suspend operator fun invoke(poolId: PoolId?): SuggestedDefaults {
        val last = movements.lastExpense()
        val account = last?.accountId?.takeIf { accounts.find(it)?.isArchived == false }
            ?: accounts.observeAccounts().first().firstOrNull { !it.account.isArchived }?.account?.id
        val pool = poolId?.let { pools.find(it) }?.takeIf { !it.isArchived }
        val lastCategory = last?.parts?.firstOrNull { it.kind == PartKind.NORMAL }?.categoryId
            ?.takeIf { categories.find(it)?.isArchived == false }
        val category = pool?.let { PoolDefaults.categoryFor(it, lastCategory) } ?: lastCategory
        return SuggestedDefaults(account, category, pool?.let { PoolDefaults.tagsFor(it, emptySet()) }.orEmpty())
    }
}
