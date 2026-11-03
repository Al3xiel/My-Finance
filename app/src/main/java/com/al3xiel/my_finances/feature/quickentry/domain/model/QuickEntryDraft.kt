package com.al3xiel.my_finances.feature.quickentry.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId

/** Una vista previa completa y sin ambigüedades, lista para guardarse con la fecha de hoy. */
sealed interface QuickEntryDraft {
    val accountId: AccountId
    val amount: Money
    val note: String?

    data class Expense(
        override val accountId: AccountId,
        override val amount: Money,
        val categoryId: CategoryId,
        val tagIds: Set<TagId>,
        val poolId: PoolId?,
        val fundId: FundId?,
        override val note: String?,
    ) : QuickEntryDraft

    data class Income(
        override val accountId: AccountId,
        override val amount: Money,
        val incomeSourceId: IncomeSourceId,
        override val note: String?,
    ) : QuickEntryDraft
}
