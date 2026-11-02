package com.al3xiel.my_finances.feature.expectedincome.domain.usecase

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeEdited
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeRules
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import java.time.LocalDate
import javax.inject.Inject

/** Los campos que el usuario quiere cambiar; `null` = dejar como está. */
data class ExpectedIncomeChanges(
    val incomeSourceId: IncomeSourceId? = null,
    val accountId: AccountId? = null,
    val estimated: Money? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val repeatsMonthly: Boolean? = null,
)

/** Corrige un ingreso esperado mientras esté pendiente (programado o vencido). Sin cambios reales no registra nada. */
class EditExpectedIncomeUseCase @Inject constructor(
    private val rules: MovementRules,
    private val expectedIncomes: ExpectedIncomeRepository,
    private val recorder: EventRecorder,
    private val lock: MovementWriteLock,
) {
    suspend operator fun invoke(id: ExpectedIncomeId, changes: ExpectedIncomeChanges): Outcome<Unit> =
        lock.withLock { execute(id, changes) }

    private suspend fun execute(id: ExpectedIncomeId, changes: ExpectedIncomeChanges): Outcome<Unit> {
        val current = expectedIncomes.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (!ExpectedIncomeRules.isPending(current.status)) return Outcome.Failed(BusinessError.NotPending)

        // Solo viaja lo que de verdad cambia; una cuenta o fuente archivada que no se toca no estorba.
        val source = changes.incomeSourceId?.takeIf { it != current.incomeSourceId }
        val account = changes.accountId?.takeIf { it != current.accountId }
        val estimated = changes.estimated?.takeIf { it != current.estimated }
        val from = changes.from?.takeIf { it != current.range.from }
        val to = changes.to?.takeIf { it != current.range.to }
        val repeats = changes.repeatsMonthly?.takeIf { it != current.repeatsMonthly }

        estimated?.let { rules.checkMoney(it) }?.let { return Outcome.Failed(it) }
        account?.let { rules.checkAccount(it) }?.let { return Outcome.Failed(it) }
        source?.let { rules.checkIncomeSource(it) }?.let { return Outcome.Failed(it) }
        ExpectedIncomeRules.validateRange(ExpectedRange(from ?: current.range.from, to ?: current.range.to))
            ?.let { return Outcome.Failed(it) }

        if (listOf(source, account, estimated, from, to, repeats).all { it == null }) return Outcome.Ok(Unit)

        recorder.record(ExpectedIncomeEdited(id, source, account, estimated, from, to, repeats))
        return Outcome.Ok(Unit)
    }
}
