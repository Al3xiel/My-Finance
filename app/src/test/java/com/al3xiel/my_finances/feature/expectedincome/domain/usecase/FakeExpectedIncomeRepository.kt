package com.al3xiel.my_finances.feature.expectedincome.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeRules
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/** Repositorio en memoria: guarda los esperados tal como los quiere la prueba, con el estado efectivo ya puesto. */
class FakeExpectedIncomeRepository : ExpectedIncomeRepository {
    val items = linkedMapOf<ExpectedIncomeId, ExpectedIncome>()

    override suspend fun find(id: ExpectedIncomeId) = items[id]
    override fun observeAll(): Flow<List<ExpectedIncome>> = flowOf(items.values.toList())

    override suspend fun pendingFor(accountId: AccountId, incomeSourceId: IncomeSourceId) =
        items.values.filter { ExpectedIncomeRules.isPending(it.status) && it.accountId == accountId && it.incomeSourceId == incomeSourceId }

    override suspend fun overdue() = items.values.filter { it.status == ExpectedIncomeStatus.OVERDUE }
    override suspend fun next() = items.values.firstOrNull { it.status == ExpectedIncomeStatus.SCHEDULED }
    override suspend fun confirmedBy(movementId: MovementId) =
        items.values.firstOrNull { it.status == ExpectedIncomeStatus.CONFIRMED && it.confirmedMovementId == movementId }

    fun put(
        id: String,
        account: AccountId,
        source: IncomeSourceId,
        estimated: Long = 200_000,
        from: LocalDate = LocalDate.of(2026, 10, 28),
        to: LocalDate = LocalDate.of(2026, 11, 5),
        repeats: Boolean = false,
        status: ExpectedIncomeStatus = ExpectedIncomeStatus.SCHEDULED,
        confirmedMovementId: MovementId? = null,
    ) = ExpectedIncome(
        ExpectedIncomeId(id), source, account, Money(estimated, CurrencyCode.PEN), ExpectedRange(from, to), repeats, status,
        cancelReason = null, confirmedMovementId = confirmedMovementId, nextExpectedId = null,
    ).also { items[it.id] = it }
}
