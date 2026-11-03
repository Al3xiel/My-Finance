package com.al3xiel.my_finances.data.projection.expectedincome

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus

/**
 * `status` guarda solo `SCHEDULED`, `CONFIRMED` y `CANCELLED`: `OVERDUE` y "vuelve a pendiente" se calculan al
 * consultar uniendo `movements` (D27).
 */
@Entity(tableName = "expected_incomes", indices = [Index("status")])
data class ExpectedIncomeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "income_source_id") val incomeSourceId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "estimated_minor") val estimatedMinor: Long,
    @ColumnInfo(name = "expected_from") val expectedFrom: String,
    @ColumnInfo(name = "expected_to") val expectedTo: String,
    @ColumnInfo(name = "repeats_monthly") val repeatsMonthly: Boolean,
    val status: ExpectedIncomeStatus,
    @ColumnInfo(name = "cancel_reason") val cancelReason: String?,
    @ColumnInfo(name = "confirmed_movement_id") val confirmedMovementId: String?,
    @ColumnInfo(name = "next_expected_id") val nextExpectedId: String?,
)
