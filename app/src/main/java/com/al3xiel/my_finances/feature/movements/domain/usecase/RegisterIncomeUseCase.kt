package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import java.time.LocalDate
import javax.inject.Inject

class RegisterIncomeUseCase @Inject constructor(
    private val rules: MovementRules,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        accountId: AccountId,
        amount: Money,
        incomeSourceId: IncomeSourceId,
        note: String?,
        occurredOn: LocalDate,
    ): Outcome<MovementId> {
        rules.checkMoney(amount)?.let { return Outcome.Failed(it) }
        rules.checkDate(occurredOn)?.let { return Outcome.Failed(it) }
        rules.checkAccount(accountId)?.let { return Outcome.Failed(it) }
        rules.checkIncomeSource(incomeSourceId)?.let { return Outcome.Failed(it) }

        val id = MovementId(ids.newId())
        recorder.record(IncomeRegistered(id, accountId, amount, incomeSourceId, null, note), occurredOn)
        return Outcome.Ok(id)
    }
}
