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

/** Cúmulo, fondo y reembolso no existen todavía: sus migraciones agregan las columnas. */
@Entity(tableName = "movement_parts", indices = [Index("movement_id"), Index("category_id")])
data class MovementPartEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "movement_id") val movementId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val kind: PartKind,
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
