package com.al3xiel.my_finances.feature.expectedincome.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeScheduled
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeRules
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import javax.inject.Inject

/** Programa un ingreso esperado. El rango puede estar en el pasado, pero `from ≤ to`; cuenta y fuente deben estar activas. */
class ScheduleExpectedIncomeUseCase @Inject constructor(
    private val rules: MovementRules,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        accountId: AccountId,
        incomeSourceId: IncomeSourceId,
        estimated: Money,
        range: ExpectedRange,
        repeatsMonthly: Boolean,
    ): Outcome<ExpectedIncomeId> {
        rules.checkMoney(estimated)?.let { return Outcome.Failed(it) }
        ExpectedIncomeRules.validateRange(range)?.let { return Outcome.Failed(it) }
        rules.checkAccount(accountId)?.let { return Outcome.Failed(it) }
        rules.checkIncomeSource(incomeSourceId)?.let { return Outcome.Failed(it) }

        val id = ExpectedIncomeId(ids.newId())
        recorder.record(ExpectedIncomeScheduled(id, incomeSourceId, accountId, estimated, range.from, range.to, repeatsMonthly))
        return Outcome.Ok(id)
    }
}
