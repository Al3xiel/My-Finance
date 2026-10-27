package com.al3xiel.my_finances.feature.movements.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import java.time.LocalDate

/** Parte que el usuario edita. `partId` solo se usa al corregir: una parte que sigue conserva su id (D15). */
data class ExpensePartInput(
    val partId: PartId?,
    val amount: Money,
    val categoryId: CategoryId,
    val tagIds: List<TagId>,
    val poolId: PoolId? = null,
)

/** Lo que el usuario deja tras corregir un movimiento; el tipo no cambia (D15). */
sealed interface MovementDraft {
    val accountId: AccountId
    val note: String?
    val occurredOn: LocalDate

    data class Income(
        override val accountId: AccountId,
        val amount: Money,
        val incomeSourceId: IncomeSourceId,
        override val note: String?,
        override val occurredOn: LocalDate,
    ) : MovementDraft

    data class Expense(
        override val accountId: AccountId,
        val total: Money,
        val parts: List<ExpensePartInput>,
        override val note: String?,
        override val occurredOn: LocalDate,
    ) : MovementDraft
}
