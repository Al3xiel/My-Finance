package com.al3xiel.my_finances.feature.loans.domain.repository

import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.feature.loans.domain.model.Loan
import com.al3xiel.my_finances.feature.loans.domain.model.LoanDetail
import kotlinx.coroutines.flow.Flow

interface LoanRepository {
    /** `null` si no existe o su movimiento está anulado. */
    suspend fun find(id: LoanId): Loan?

    suspend fun findByMovement(movementId: MovementId): Loan?

    /** Detalle de la deuda (S36) con sus pagos vigentes; `null` si no existe o está anulada. */
    fun observeDetail(id: LoanId): Flow<LoanDetail?>
}
