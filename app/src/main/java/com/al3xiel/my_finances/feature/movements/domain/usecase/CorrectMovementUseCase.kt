package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.movements.domain.model.Movement
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.UnchangedReferences
import javax.inject.Inject

/** Corrige un ingreso o un gasto con el estado completo resultante y un motivo (D15). Transferencias: no (D12). */
class CorrectMovementUseCase @Inject constructor(
    private val rules: MovementRules,
    private val accounts: AccountRepository,
    private val movements: MovementRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val lock: MovementWriteLock,
) {
    suspend operator fun invoke(
        id: MovementId,
        draft: MovementDraft,
        reason: String,
        confirmNegativeBalance: Boolean = false,
    ): Outcome<Unit> = lock.withLock { execute(id, draft, reason, confirmNegativeBalance) }

    private suspend fun execute(
        id: MovementId,
        draft: MovementDraft,
        reason: String,
        confirmNegativeBalance: Boolean,
    ): Outcome<Unit> {
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) return Outcome.Failed(BusinessError.ReasonRequired)
        val current = movements.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (current.type == MovementType.TRANSFER) return Outcome.Failed(BusinessError.TransferNotEditable)
        if (current.status == MovementStatus.VOIDED) return Outcome.Failed(BusinessError.AlreadyVoided)

        rules.checkDate(draft.occurredOn)?.let { return Outcome.Failed(it) }
        rules.checkAccount(draft.accountId, allowArchived = draft.accountId == current.accountId)?.let { return Outcome.Failed(it) }
        val snapshot = when (draft) {
            is MovementDraft.Income -> incomeSnapshot(current, draft)
            is MovementDraft.Expense -> expenseSnapshot(current, draft)
        }.let { result ->
            when (result) {
                is Outcome.Failed -> return result
                is Outcome.Ok -> result.value
            }
        }

        if (!confirmNegativeBalance) {
            val balance = accounts.balanceMinor(draft.accountId)
            val resulting = balance - effectOnAccount(current, draft.accountId) + effectOf(snapshot)
            rules.negativeBalanceWarning(balance, resulting)?.let { return Outcome.Failed(it) }
        }

        recorder.record(MovementCorrected(id, snapshot, cleanReason, null), draft.occurredOn)
        return Outcome.Ok(Unit)
    }

    private suspend fun incomeSnapshot(current: Movement, draft: MovementDraft.Income): Outcome<MovementSnapshot> {
        if (current.type != MovementType.INCOME) return Outcome.Failed(BusinessError.TypeChangeNotAllowed)
        rules.checkMoney(draft.amount)?.let { return Outcome.Failed(it) }
        rules.checkIncomeSource(draft.incomeSourceId)?.let { return Outcome.Failed(it) }
        return Outcome.Ok(MovementSnapshot.Income(draft.accountId, draft.amount, draft.incomeSourceId, draft.note))
    }

    private suspend fun expenseSnapshot(current: Movement, draft: MovementDraft.Expense): Outcome<MovementSnapshot> {
        if (current.type != MovementType.EXPENSE) return Outcome.Failed(BusinessError.TypeChangeNotAllowed)
        val existing = current.parts.map { it.id }.toSet()
        val kept = draft.parts.mapNotNull { it.partId }
        if (kept.any { it !in existing } || kept.size != kept.toSet().size) return Outcome.Failed(BusinessError.InvalidPart)
        val unchanged = UnchangedReferences(
            categories = current.parts.associate { it.id to it.categoryId },
            tags = current.parts.associate { it.id to it.tagIds.toSet() },
        )
        rules.checkExpense(draft.total, draft.parts, unchanged)?.let { return Outcome.Failed(it) }

        val parts = draft.parts.map {
            ExpensePartData(it.partId ?: PartId(ids.newId()), it.amount, it.categoryId, it.tagIds, null, null)
        }
        return Outcome.Ok(MovementSnapshot.Expense(draft.accountId, draft.total, parts, draft.note))
    }

    private fun effectOf(snapshot: MovementSnapshot): Long = when (snapshot) {
        is MovementSnapshot.Income -> snapshot.amount.amountMinor
        is MovementSnapshot.Expense -> -snapshot.total.amountMinor
    }

    /** Cuánto movió el movimiento actual en esa cuenta (0 si estaba en otra). */
    private fun effectOnAccount(current: Movement, accountId: AccountId): Long = when {
        current.accountId != accountId -> 0L
        current.type == MovementType.INCOME -> current.amount.amountMinor
        else -> -current.amount.amountMinor
    }
}
