package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.MovementVoidGuard
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
) {
    /** Anular un ingreso resta ese dinero de la cuenta: si queda negativa avisa, y repetir con `confirmNegativeBalance` anula (D14). */
    suspend operator fun invoke(id: MovementId, reason: String, confirmNegativeBalance: Boolean = false): Outcome<Unit> =
        lock.withLock { execute(id, reason, confirmNegativeBalance) }

    private suspend fun execute(id: MovementId, reason: String, confirmNegativeBalance: Boolean): Outcome<Unit> {
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) return Outcome.Failed(BusinessError.ReasonRequired)
        val movement = movements.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (movement.type == MovementType.TRANSFER) return Outcome.Failed(BusinessError.TransferNotEditable)
        if (movement.status == MovementStatus.VOIDED) return Outcome.Failed(BusinessError.AlreadyVoided)
        guards.firstNotNullOfOrNull { it.check(id) }?.let { return Outcome.Failed(it) }
        if (!confirmNegativeBalance) {
            val balance = accounts.balanceMinor(movement.accountId)
            // Anular lo que sumó (ingreso, reembolso, pago de préstamo) resta de la cuenta; lo que restó (gasto, préstamo) devuelve.
            val addedMoney = movement.type in setOf(MovementType.INCOME, MovementType.REIMBURSEMENT, MovementType.LOAN_REPAYMENT)
            val effect = if (addedMoney) -movement.amount.amountMinor else movement.amount.amountMinor
            rules.negativeBalanceWarning(balance, balance + effect)?.let { return Outcome.Failed(it) }
        }

        recorder.record(MovementVoided(id, cleanReason, null))
        return Outcome.Ok(Unit)
    }
}
