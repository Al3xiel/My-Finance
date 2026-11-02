package com.al3xiel.my_finances.feature.expectedincome.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeConfirmed
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeScheduled
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeRules
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.NextOccurrence
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.rules.FundRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Registra el ingreso real que confirma un esperado (S23), en una sola correlación: `IncomeRegistered`, la recarga
 * opcional de un fondo, `ExpectedIncomeConfirmed` y, si repite cada mes, el siguiente `ExpectedIncomeScheduled` (D29).
 * El siguiente sale del esperado programado (misma cuenta, fuente y estimado), no del ingreso real (P3).
 *
 * Un esperado cuya cuenta o fuente quedó archivada no se confirma hasta editarlo.
 */
class ConfirmExpectedIncomeUseCase @Inject constructor(
    private val rules: MovementRules,
    private val expectedIncomes: ExpectedIncomeRepository,
    private val funds: FundRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val lock: MovementWriteLock,
) {
    suspend operator fun invoke(
        expectedIncomeId: ExpectedIncomeId,
        accountId: AccountId,
        amount: Money,
        incomeSourceId: IncomeSourceId,
        note: String?,
        occurredOn: LocalDate,
        fundId: FundId? = null,
        topUp: Money? = null,
    ): Outcome<MovementId> = lock.withLock {
        execute(expectedIncomeId, accountId, amount, incomeSourceId, note, occurredOn, fundId, topUp)
    }

    private suspend fun execute(
        expectedIncomeId: ExpectedIncomeId,
        accountId: AccountId,
        amount: Money,
        incomeSourceId: IncomeSourceId,
        note: String?,
        occurredOn: LocalDate,
        fundId: FundId?,
        topUp: Money?,
    ): Outcome<MovementId> {
        val expected = expectedIncomes.find(expectedIncomeId) ?: return Outcome.Failed(BusinessError.NotFound)
        if (!ExpectedIncomeRules.isPending(expected.status)) return Outcome.Failed(BusinessError.NotPending)
        rules.checkAccount(expected.accountId)?.let { return Outcome.Failed(it) }
        rules.checkIncomeSource(expected.incomeSourceId)?.let { return Outcome.Failed(it) }
        rules.checkIncome(accountId, amount, incomeSourceId, occurredOn)?.let { return Outcome.Failed(it) }

        if ((fundId == null) != (topUp == null)) return Outcome.Failed(BusinessError.InvalidAmount)
        if (fundId != null && topUp != null) {
            val fund = funds.find(fundId) ?: return Outcome.Failed(BusinessError.NotFound)
            if (fund.fund.status == FundStatus.CLOSED) return Outcome.Failed(BusinessError.FundClosed)
            FundRules.checkIncomeTopUp(topUp, amount, Money.zero(amount.currency))?.let { return Outcome.Failed(it) }
        }

        val movementId = MovementId(ids.newId())
        val nextId = if (expected.repeatsMonthly) ExpectedIncomeId(ids.newId()) else null

        val events = buildList<NewEvent> {
            add(NewEvent(IncomeRegistered(movementId, accountId, amount, incomeSourceId, null, note), occurredOn))
            if (fundId != null && topUp != null) add(NewEvent(FundToppedUp(fundId, topUp, movementId), occurredOn))
            add(NewEvent(ExpectedIncomeConfirmed(expectedIncomeId, movementId, nextId), occurredOn))
            if (nextId != null) {
                val next = NextOccurrence.of(expected.range)
                add(
                    NewEvent(
                        ExpectedIncomeScheduled(
                            nextId, expected.incomeSourceId, expected.accountId, expected.estimated, next.from, next.to, true,
                        ),
                        recorder.today(),
                    ),
                )
            }
        }
        recorder.record(events)
        return Outcome.Ok(movementId)
    }
}
