package com.al3xiel.my_finances.data.projection.loan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

/** Escritura para el proyector de deudas; las consultas de pantalla vienen en T15. */
@Dao
interface LoanDao {
    @Upsert suspend fun upsertLoan(loan: LoanEntity)

    @Query("SELECT * FROM loans WHERE id = :id") suspend fun loan(id: String): LoanEntity?

    @Query("SELECT * FROM loans WHERE movement_id = :movementId") suspend fun loanByMovement(movementId: String): LoanEntity?

    @Insert suspend fun insertRepayment(repayment: LoanRepaymentEntity)

    @Query("SELECT * FROM loan_repayments WHERE movement_id = :movementId")
    suspend fun repaymentByMovement(movementId: String): LoanRepaymentEntity?

    @Query("DELETE FROM loan_repayments WHERE id = :id") suspend fun deleteRepayment(id: String)

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM loan_repayments WHERE loan_id = :loanId") suspend fun repaidOf(loanId: String): Long

    @Query("DELETE FROM loans") suspend fun clearLoans()
    @Query("DELETE FROM loan_repayments") suspend fun clearRepayments()
    @Query("DELETE FROM movements WHERE type IN ('LOAN_DISBURSEMENT', 'LOAN_REPAYMENT')") suspend fun deleteOwnMovements()
    @Query("DELETE FROM ledger_entries WHERE entry_kind IN ('LOAN_OUT', 'LOAN_IN')") suspend fun deleteOwnEntries()
}
