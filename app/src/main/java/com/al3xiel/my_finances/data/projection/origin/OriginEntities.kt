package com.al3xiel.my_finances.data.projection.origin

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.al3xiel.my_finances.domain.model.LedgerEntryKind

/** Cuánto del saldo de una cuenta viene de cada fuente, en montos (docs/04 §4.6). Sin filas en cero. */
@Entity(tableName = "origin_balances", primaryKeys = ["account_id", "income_source_id"])
data class OriginBalanceEntity(
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "income_source_id") val incomeSourceId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

/** De qué fuente salió cada parte de un gasto; `part_id` vacío si el asiento no tiene parte (D41). */
@Entity(
    tableName = "origin_attributions",
    primaryKeys = ["ledger_entry_id", "part_id", "income_source_id"],
    indices = [Index("income_source_id")],
)
data class OriginAttributionEntity(
    @ColumnInfo(name = "ledger_entry_id") val ledgerEntryId: String,
    @ColumnInfo(name = "part_id") val partId: String,
    @ColumnInfo(name = "income_source_id") val incomeSourceId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

/** Una sola fila (`id` = 1): hasta qué asiento llega el estado de `origin_balances` (D40). */
@Entity(tableName = "origin_cursor")
data class OriginCursorEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "last_order_key") val lastOrderKey: Long,
    @ColumnInfo(name = "last_sequence") val lastSequence: Long,
    @ColumnInfo(name = "last_entry_id") val lastEntryId: String,
)

/** Un asiento vigente con la fuente de su movimiento y su clave de orden (menor `sequence` del movimiento, con anulados). */
data class OriginEntryRow(
    val id: String,
    val sequence: Long,
    @ColumnInfo(name = "movement_id") val movementId: String?,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "delta_minor") val deltaMinor: Long,
    @ColumnInfo(name = "entry_kind") val entryKind: LedgerEntryKind,
    @ColumnInfo(name = "source_id") val sourceId: String?,
    @ColumnInfo(name = "order_key") val orderKey: Long,
)

data class OriginPartRow(
    val id: String,
    @ColumnInfo(name = "movement_id") val movementId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)
