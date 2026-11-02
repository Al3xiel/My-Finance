package com.al3xiel.my_finances.feature.funds.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.rules.FundRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import java.time.LocalDate
import javax.inject.Inject

/** Registra un ingreso y aparta parte de él en un fondo: los dos eventos van en una sola correlación (D24). */
class RegisterIncomeWithTopUpUseCase @Inject constructor(
    private val rules: MovementRules,
    private val funds: FundRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val lock: MovementWriteLock,
) {
    suspend operator fun invoke(
        accountId: AccountId,
        amount: Money,
        incomeSourceId: IncomeSourceId,
        note: String?,
        occurredOn: LocalDate,
        fundId: FundId,
        topUp: Money,
    ): Outcome<MovementId> = lock.withLock { execute(accountId, amount, incomeSourceId, note, occurredOn, fundId, topUp) }

    private suspend fun execute(
        accountId: AccountId,
        amount: Money,
        incomeSourceId: IncomeSourceId,
        note: String?,
        occurredOn: LocalDate,
        fundId: FundId,
        topUp: Money,
    ): Outcome<MovementId> {
        rules.checkIncome(accountId, amount, incomeSourceId, occurredOn)?.let { return Outcome.Failed(it) }
        val fund = funds.find(fundId) ?: return Outcome.Failed(BusinessError.NotFound)
        if (fund.fund.status == FundStatus.CLOSED) return Outcome.Failed(BusinessError.FundClosed)
        // Un ingreso nuevo no tiene recargas previas.
        FundRules.checkIncomeTopUp(topUp, amount, Money.zero(amount.currency))?.let { return Outcome.Failed(it) }

        val id = MovementId(ids.newId())
        recorder.record(
            listOf(
                NewEvent(IncomeRegistered(id, accountId, amount, incomeSourceId, null, note), occurredOn),
                NewEvent(FundToppedUp(fundId, topUp, id), occurredOn),
            ),
        )
        return Outcome.Ok(id)
    }
}
