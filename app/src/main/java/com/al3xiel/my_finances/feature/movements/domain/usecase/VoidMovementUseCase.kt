package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.MovementVoidFollowUp
import com.al3xiel.my_finances.domain.usecase.MovementVoidGuard
import com.al3xiel.my_finances.domain.usecase.ReconciliationLinkGuard
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import javax.inject.Inject

class VoidMovementUseCase @Inject constructor(
    private val movements: MovementRepository,
    private val accounts: AccountRepository,
    private val rules: MovementRules,
    private val recorder: EventRecorder,
    private val lock: MovementWriteLock,
    private val guards: Set<@JvmSuppressWildcards MovementVoidGuard> = emptySet(),
    private val followUps: Set<@JvmSuppressWildcards MovementVoidFollowUp> = emptySet(),
    private val linkGuards: Set<@JvmSuppressWildcards ReconciliationLinkGuard> = emptySet(),
) {
    /** Anular un ingreso resta ese dinero de la cuenta: si queda negativa avisa, y repetir con `confirmNegativeBalance` anula (D14). */
    suspend operator fun invoke(
        id: MovementId,
        reason: String,
        confirmNegativeBalance: Boolean = false,
        reconciliationId: ReconciliationId? = null,
    ): Outcome<Unit> = lock.withLock { execute(id, reason, confirmNegativeBalance, reconciliationId) }

    private suspend fun execute(
        id: MovementId,
        reason: String,
        confirmNegativeBalance: Boolean,
        reconciliationId: ReconciliationId?,
    ): Outcome<Unit> {
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) return Outcome.Failed(BusinessError.ReasonRequired)
        val movement = movements.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (movement.type == MovementType.TRANSFER) return Outcome.Failed(BusinessError.TransferNotEditable)
        if (movement.status == MovementStatus.VOIDED) return Outcome.Failed(BusinessError.AlreadyVoided)
        guards.firstNotNullOfOrNull { it.check(id) }?.let { return Outcome.Failed(it) }
        reconciliationId?.let { rid ->
            val touched = setOfNotNull(movement.accountId, movement.counterAccountId)
            linkGuards.firstNotNullOfOrNull { it.check(rid, touched) }?.let { return Outcome.Failed(it) }
        }
        val extraEvents = mutableListOf<NewEvent>()
        for (followUp in followUps) {
            when (val result = followUp.followUp(id)) {
                is Outcome.Failed -> return result
                is Outcome.Ok -> result.value.forEach { extraEvents += NewEvent(it, recorder.today()) }
            }
        }
        if (!confirmNegativeBalance) {
            val balance = accounts.balanceMinor(movement.accountId)
            // Anular lo que sumó (ingreso, reembolso, pago de préstamo) resta de la cuenta; lo que restó (gasto, préstamo) devuelve.
            val addedMoney = movement.type in setOf(MovementType.INCOME, MovementType.REIMBURSEMENT, MovementType.LOAN_REPAYMENT) ||
                (movement.type == MovementType.ADJUSTMENT && movement.incomeSourceId != null) // ingreso no identificado
            val effect = if (addedMoney) -movement.amount.amountMinor else movement.amount.amountMinor
            rules.negativeBalanceWarning(balance, balance + effect)?.let { return Outcome.Failed(it) }
        }

        recorder.record(listOf(NewEvent(MovementVoided(id, cleanReason, reconciliationId), recorder.today())) + extraEvents)
        return Outcome.Ok(Unit)
    }
}
