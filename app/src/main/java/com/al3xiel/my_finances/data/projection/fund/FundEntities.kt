package com.al3xiel.my_finances.data.projection.fund

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus

/**
 * El gastado detallado no se guarda: se calcula de `movement_parts` al consultar (D23). Aquí solo está lo que sale de
 * los eventos del fondo.
 */
@Entity(tableName = "funds")
data class FundEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "consumption_mode") val consumptionMode: ConsumptionMode,
    @ColumnInfo(name = "daily_estimate_minor") val dailyEstimateMinor: Long?,
    @ColumnInfo(name = "starts_on") val startsOn: String,
    @ColumnInfo(name = "ends_on") val endsOn: String?,
    @ColumnInfo(name = "allocated_minor") val allocatedMinor: Long,
    @ColumnInfo(name = "untracked_spent_minor") val untrackedSpentMinor: Long,
    val status: FundStatus,
    @ColumnInfo(name = "remainder_policy") val remainderPolicy: RemainderPolicy?,
    @ColumnInfo(name = "rollover_to_fund_id") val rolloverToFundId: String?,
    @ColumnInfo(name = "closed_on") val closedOn: String?,
)

@Entity(tableName = "fund_usage_days", primaryKeys = ["fund_id", "day"])
data class FundUsageDayEntity(
    @ColumnInfo(name = "fund_id") val fundId: String,
    val day: String,
)

/** `id` = `<sequence del evento>-count`. Estimado y desviación solo existen en modo estimado. */
@Entity(tableName = "fund_counts", indices = [Index("fund_id")])
data class FundCountEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "fund_id") val fundId: String,
    @ColumnInfo(name = "declared_remaining_minor") val declaredRemainingMinor: Long,
    @ColumnInfo(name = "estimated_spent_minor") val estimatedSpentMinor: Long?,
    @ColumnInfo(name = "real_spent_minor") val realSpentMinor: Long,
    @ColumnInfo(name = "deviation_minor") val deviationMinor: Long?,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
)

/** `id` = `<sequence del evento>-topup` (D24). */
@Entity(tableName = "fund_topups", indices = [Index("fund_id"), Index("linked_income_movement_id")])
data class FundTopUpEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "fund_id") val fundId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "linked_income_movement_id") val linkedIncomeMovementId: String?,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
)
