package com.al3xiel.my_finances.data.projection.pool

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus

/** `default_tag_ids`: IDs separados por coma (UUID, sin comas); vacío si no hay. */
@Entity(tableName = "pools", indices = [Index("responsible_person_id")])
data class PoolEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "responsible_person_id") val responsiblePersonId: String,
    val kind: PoolKind,
    @ColumnInfo(name = "default_category_id") val defaultCategoryId: String?,
    @ColumnInfo(name = "default_tag_ids") val defaultTagIds: String,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
)

@Entity(tableName = "pool_periods", indices = [Index("pool_id")])
data class PoolPeriodEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "pool_id") val poolId: String,
    @ColumnInfo(name = "opened_on") val openedOn: String,
    @ColumnInfo(name = "closed_on") val closedOn: String?,
    val status: PoolPeriodStatus,
    @ColumnInfo(name = "statement_id") val statementId: String?,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
    @ColumnInfo(name = "reimbursed_minor") val reimbursedMinor: Long,
)

@Entity(tableName = "settlement_statements", indices = [Index("pool_id"), Index("period_id")])
data class SettlementStatementEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "pool_id") val poolId: String,
    @ColumnInfo(name = "period_id") val periodId: String,
    @ColumnInfo(name = "pending_total_minor") val pendingTotalMinor: Long,
    val format: StatementFormat,
    @ColumnInfo(name = "generated_on") val generatedOn: String,
)

@Entity(tableName = "reimbursements", indices = [Index("person_id"), Index("movement_id")])
data class ReimbursementEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "person_id") val personId: String,
    @ColumnInfo(name = "movement_id") val movementId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val strategy: AllocationStrategy,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
)

@Entity(
    tableName = "reimbursement_allocations",
    primaryKeys = ["reimbursement_id", "part_id"],
    indices = [Index("part_id")],
)
data class ReimbursementAllocationEntity(
    @ColumnInfo(name = "reimbursement_id") val reimbursementId: String,
    @ColumnInfo(name = "part_id") val partId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)
