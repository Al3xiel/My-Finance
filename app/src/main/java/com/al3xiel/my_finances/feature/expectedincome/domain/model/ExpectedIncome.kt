package com.al3xiel.my_finances.feature.expectedincome.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange

/**
 * Un ingreso esperado con su estado **efectivo** (D27): `OVERDUE` ya resuelto contra el día de hoy y un confirmado cuyo
 * movimiento se anuló aparece de nuevo como pendiente (sin `confirmedMovementId`).
 */
data class ExpectedIncome(
    val id: ExpectedIncomeId,
    val incomeSourceId: IncomeSourceId,
    val accountId: AccountId,
    val estimated: Money,
    val range: ExpectedRange,
    val repeatsMonthly: Boolean,
    val status: ExpectedIncomeStatus,
    val cancelReason: String?,
    val confirmedMovementId: MovementId?,
    val nextExpectedId: ExpectedIncomeId?,
)
