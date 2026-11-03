package com.al3xiel.my_finances.feature.loans.domain.rules

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.usecase.MovementVoidGuard
import com.al3xiel.my_finances.feature.loans.domain.repository.LoanRepository
import javax.inject.Inject

/** Un préstamo con pagos vigentes no se anula hasta anular los pagos (D20). */
class LoanVoidGuard @Inject constructor(
    private val loans: LoanRepository,
) : MovementVoidGuard {
    override suspend fun check(movementId: MovementId): BusinessError? {
        val loan = loans.findByMovement(movementId) ?: return null
        return if (loan.repaid.isPositive) BusinessError.HasRepayments else null
    }
}
