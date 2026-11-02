package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.ReconciliationLinkGuard
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import java.time.LocalDate
import javax.inject.Inject

class RegisterIncomeUseCase @Inject constructor(
    private val rules: MovementRules,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val linkGuards: Set<@JvmSuppressWildcards ReconciliationLinkGuard> = emptySet(),
) {
    suspend operator fun invoke(
        accountId: AccountId,
        amount: Money,
        incomeSourceId: IncomeSourceId,
        note: String?,
        occurredOn: LocalDate,
        reconciliationId: ReconciliationId? = null,
    ): Outcome<MovementId> {
        rules.checkIncome(accountId, amount, incomeSourceId, occurredOn)?.let { return Outcome.Failed(it) }
        reconciliationId?.let { rid -> linkGuards.firstNotNullOfOrNull { it.check(rid, setOf(accountId)) }?.let { return Outcome.Failed(it) } }

        val id = MovementId(ids.newId())
        recorder.record(IncomeRegistered(id, accountId, amount, incomeSourceId, reconciliationId, note), occurredOn)
        return Outcome.Ok(id)
    }
}
