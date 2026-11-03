package com.al3xiel.my_finances.data.projection.loan

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import kotlinx.coroutines.flow.Flow

data class LoanDetailRow(
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "person_id") val personId: String,
    val alias: String,
    @ColumnInfo(name = "movement_id") val movementId: String,
    @ColumnInfo(name = "principal_minor") val principalMinor: Long,
    @ColumnInfo(name = "repaid_minor") val repaidMinor: Long,
    val status: LoanStatus,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
)

data class RepaymentRow(
    @ColumnInfo(name = "movement_id") val movementId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
)

/** Lecturas de deudas; un préstamo anulado no existe para quien consulta. */
@Dao
interface LoanQueryDao {
    @Query("SELECT l.* FROM loans l JOIN movements m ON m.id = l.movement_id AND m.status = 'ACTIVE' WHERE l.id = :id")
    suspend fun loan(id: String): LoanEntity?

    @Query("SELECT l.* FROM loans l JOIN movements m ON m.id = l.movement_id AND m.status = 'ACTIVE' WHERE l.movement_id = :movementId")
    suspend fun loanByMovement(movementId: String): LoanEntity?

    @Query(
        """
        SELECT l.id AS loan_id, l.person_id AS person_id, pe.alias AS alias, l.movement_id AS movement_id,
               l.principal_minor AS principal_minor, l.repaid_minor AS repaid_minor, l.status AS status, l.occurred_on AS occurred_on
        FROM loans l
        JOIN movements m ON m.id = l.movement_id AND m.status = 'ACTIVE'
        JOIN people pe ON pe.id = l.person_id
        WHERE l.id = :id
        """,
    )
    fun observeLoan(id: String): Flow<LoanDetailRow?>

    @Query(
        "SELECT movement_id, amount_minor, occurred_on FROM loan_repayments WHERE loan_id = :loanId ORDER BY occurred_on DESC, id DESC",
    )
    fun observeRepayments(loanId: String): Flow<List<RepaymentRow>>
}
