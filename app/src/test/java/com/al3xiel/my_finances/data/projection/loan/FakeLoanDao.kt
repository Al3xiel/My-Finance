package com.al3xiel.my_finances.data.projection.loan

import com.al3xiel.my_finances.data.projection.movement.FakeLedgerDao
import com.al3xiel.my_finances.data.projection.movement.FakeMovementDao
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementType

class FakeLoanDao(
    private val movementDao: FakeMovementDao,
    private val ledgerDao: FakeLedgerDao,
) : LoanDao {
    val loans = linkedMapOf<String, LoanEntity>()
    val repayments = linkedMapOf<String, LoanRepaymentEntity>()

    override suspend fun upsertLoan(loan: LoanEntity) { loans[loan.id] = loan }
    override suspend fun loan(id: String) = loans[id]
    override suspend fun loanByMovement(movementId: String) = loans.values.firstOrNull { it.movementId == movementId }

    override suspend fun insertRepayment(repayment: LoanRepaymentEntity) { repayments[repayment.id] = repayment }
    override suspend fun repaymentByMovement(movementId: String) = repayments.values.firstOrNull { it.movementId == movementId }
    override suspend fun deleteRepayment(id: String) { repayments.remove(id) }
    override suspend fun repaidOf(loanId: String) = repayments.values.filter { it.loanId == loanId }.sumOf { it.amountMinor }

    override suspend fun clearLoans() = loans.clear()
    override suspend fun clearRepayments() = repayments.clear()

    override suspend fun deleteOwnMovements() {
        movementDao.movements.values.removeAll { it.type == MovementType.LOAN_DISBURSEMENT || it.type == MovementType.LOAN_REPAYMENT }
    }

    override suspend fun deleteOwnEntries() {
        ledgerDao.entries.values.removeAll { it.entryKind == LedgerEntryKind.LOAN_OUT || it.entryKind == LedgerEntryKind.LOAN_IN }
    }
}
