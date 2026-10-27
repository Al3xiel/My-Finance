package com.al3xiel.my_finances.data.projection.movement

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.feature.pools.domain.rules.ReimbursementStatus

@Entity(tableName = "movements", indices = [Index("account_id"), Index("occurred_on")])
data class MovementEntity(
    @PrimaryKey val id: String,
    val type: MovementType,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "counter_account_id") val counterAccountId: String?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: CurrencyCode,
    @ColumnInfo(name = "income_source_id") val incomeSourceId: String?,
    @ColumnInfo(name = "person_id") val personId: String?,
    val note: String?,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
    val status: MovementStatus,
    val revision: Int,
    @ColumnInfo(name = "last_sequence") val lastSequence: Long,
)

/**
 * Dueños de cada columna (los proyectores corren uno tras otro en el replay): `pool_id` y `created_sequence`, el de
 * movimientos; `pool_period_id`, el de cúmulos; `reimbursed_minor` y `reimbursement_status`, el de reembolsos.
 * Fondo no existe todavía: su migración agrega la columna.
 */
@Entity(tableName = "movement_parts", indices = [Index("movement_id"), Index("category_id"), Index("pool_id")])
data class MovementPartEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "movement_id") val movementId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val kind: PartKind,
    @ColumnInfo(name = "pool_id") val poolId: String? = null,
    @ColumnInfo(name = "pool_period_id") val poolPeriodId: String? = null,
    @ColumnInfo(name = "reimbursed_minor", defaultValue = "0") val reimbursedMinor: Long = 0,
    @ColumnInfo(name = "reimbursement_status", defaultValue = "PENDING")
    val reimbursementStatus: ReimbursementStatus = ReimbursementStatus.PENDING,
    @ColumnInfo(name = "created_sequence", defaultValue = "0") val createdSequence: Long = 0,
)

@Entity(tableName = "movement_part_tags", primaryKeys = ["part_id", "tag_id"], indices = [Index("tag_id")])
data class MovementPartTagEntity(
    @ColumnInfo(name = "part_id") val partId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

/** `id` = `<sequence del evento>-<n>`: determinista, así el replay reproduce las mismas filas. */
@Entity(tableName = "ledger_entries", indices = [Index("account_id", "occurred_on"), Index("movement_id")])
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val sequence: Long,
    @ColumnInfo(name = "movement_id") val movementId: String?,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "delta_minor") val deltaMinor: Long,
    @ColumnInfo(name = "entry_kind") val entryKind: LedgerEntryKind,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
    @ColumnInfo(name = "is_voided") val isVoided: Boolean,
)

@Entity(tableName = "account_balances")
data class AccountBalanceEntity(
    @PrimaryKey @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "balance_minor") val balanceMinor: Long,
    @ColumnInfo(name = "last_sequence") val lastSequence: Long,
)
