package com.al3xiel.my_finances.data.projection.loan

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus

@Entity(tableName = "loans", indices = [Index("person_id"), Index("movement_id")])
data class LoanEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "person_id") val personId: String,
    @ColumnInfo(name = "movement_id") val movementId: String,
    @ColumnInfo(name = "principal_minor") val principalMinor: Long,
    @ColumnInfo(name = "repaid_minor") val repaidMinor: Long,
    val status: LoanStatus,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
)

@Entity(tableName = "loan_repayments", indices = [Index("loan_id"), Index("movement_id")])
data class LoanRepaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "movement_id") val movementId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
)
