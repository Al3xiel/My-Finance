package com.al3xiel.my_finances.feature.expectedincome.data

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.expectedincome.ExpectedIncomeQueryDao
import com.al3xiel.my_finances.data.projection.expectedincome.ExpectedIncomeRow
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeRules
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class ExpectedIncomeRepositoryImpl @Inject constructor(
    private val queries: ExpectedIncomeQueryDao,
    private val clock: Clock,
) : ExpectedIncomeRepository {
    private fun today() = LocalDate.now(clock)

    override suspend fun find(id: ExpectedIncomeId) = queries.expected(id.value)?.toDomain(today())

    override fun observeAll(): Flow<List<ExpectedIncome>> = queries.observeAll().map { rows -> rows.map { it.toDomain(today()) } }

    override suspend fun pendingFor(accountId: AccountId, incomeSourceId: IncomeSourceId) =
        queries.pendingFor(accountId.value, incomeSourceId.value).map { it.toDomain(today()) }

    override suspend fun overdue() = today().let { day -> queries.overdue(day.toString()).map { it.toDomain(day) } }

    override suspend fun next() = today().let { day -> queries.next(day.toString())?.toDomain(day) }

    override suspend fun confirmedBy(movementId: MovementId) = queries.confirmedBy(movementId.value)?.toDomain(today())
}

/** El estado guardado + hoy + el estado del movimiento confirmado = el estado efectivo (D27). */
internal fun ExpectedIncomeRow.toDomain(today: LocalDate): ExpectedIncome {
    val range = ExpectedRange(LocalDate.parse(expected.expectedFrom), LocalDate.parse(expected.expectedTo))
    val status = ExpectedIncomeRules.effectiveStatus(
        stored = expected.status,
        expectedTo = range.to,
        today = today,
        confirmedMovementVoided = confirmedMovementStatus == MovementStatus.VOIDED,
    )
    return ExpectedIncome(
        id = ExpectedIncomeId(expected.id),
        incomeSourceId = IncomeSourceId(expected.incomeSourceId),
        accountId = AccountId(expected.accountId),
        estimated = Money(expected.estimatedMinor, CurrencyCode.PEN),
        range = range,
        repeatsMonthly = expected.repeatsMonthly,
        status = status,
        cancelReason = expected.cancelReason,
        confirmedMovementId = expected.confirmedMovementId?.takeIf { status == ExpectedIncomeStatus.CONFIRMED }?.let(::MovementId),
        nextExpectedId = expected.nextExpectedId?.let(::ExpectedIncomeId),
    )
}
