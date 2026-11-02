package com.al3xiel.my_finances.feature.funds.domain.rules

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.usecase.MovementCorrectionGuard
import com.al3xiel.my_finances.domain.usecase.MovementVoidGuard
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import javax.inject.Inject

private val ADDS_MONEY = setOf(MovementType.INCOME, MovementType.REIMBURSEMENT, MovementType.LOAN_REPAYMENT)

/**
 * Lo que los fondos le exigen a otros movimientos: un ingreso con recargas vinculadas no se anula ni baja de lo recargado
 * (D24); el gasto no registrado de un arqueo no se anula ni se corrige (D25); y el dinero que entró no se quita si ya está
 * apartado en fondos (el Disponible libre no puede quedar en negativo).
 */
class FundMovementGuards @Inject constructor(
    private val funds: FundRepository,
    private val movements: MovementRepository,
) : MovementVoidGuard, MovementCorrectionGuard {
    override suspend fun check(movementId: MovementId): BusinessError? {
        val movement = movements.find(movementId)
        if (movement?.type == MovementType.FUND_UNTRACKED_SPEND) return BusinessError.UntrackedSpendLocked
        if (funds.toppedUpFrom(movementId).isPositive) return BusinessError.HasTopUps
        return if (movement != null && movement.type in ADDS_MONEY && wouldBreakTheReserve(movement.amount.amountMinor)) {
            BusinessError.ReservedByFunds
        } else {
            null
        }
    }

    override suspend fun check(movementId: MovementId, snapshot: MovementSnapshot): BusinessError? {
        val movement = movements.find(movementId)
        if (movement?.type == MovementType.FUND_UNTRACKED_SPEND) return BusinessError.UntrackedSpendLocked
        if (snapshot !is MovementSnapshot.Income) return null
        if (snapshot.amount.amountMinor < funds.toppedUpFrom(movementId).amountMinor) return BusinessError.BelowToppedUpAmount
        val drop = (movement?.amount?.amountMinor ?: return null) - snapshot.amount.amountMinor
        return if (drop > 0 && wouldBreakTheReserve(drop)) BusinessError.ReservedByFunds else null
    }

    /** Quitar `minor` de las cuentas dejaría el Disponible libre por debajo de cero. */
    private suspend fun wouldBreakTheReserve(minor: Long) = funds.availableBalanceMinor() < minor
}
