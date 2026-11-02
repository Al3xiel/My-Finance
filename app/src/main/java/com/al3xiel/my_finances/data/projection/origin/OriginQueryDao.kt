package com.al3xiel.my_finances.data.projection.origin

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.al3xiel.my_finances.domain.model.LedgerEntryKind

data class OriginAttributionRecordRow(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "entry_kind") val kind: LedgerEntryKind,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

/** Solo lecturas de pantalla sobre `origin_*`; la escritura la hace el proyector (`OriginDao`). Fechas ISO. */
@Dao
interface OriginQueryDao {
    @Query("SELECT * FROM origin_balances ORDER BY account_id, income_source_id")
    suspend fun balances(): List<OriginBalanceEntity>

    /** Asientos vigentes del periodo; `part_id` vacío (préstamo, ajuste −) no une con ninguna parte, así que no tiene categoría. */
    @Query(
        """
        SELECT a.income_source_id AS source_id, l.account_id AS account_id, l.entry_kind AS entry_kind,
               p.category_id AS category_id, c.parent_id AS parent_id, a.amount_minor AS amount_minor
        FROM origin_attributions a
        JOIN ledger_entries l ON l.id = a.ledger_entry_id
        LEFT JOIN movement_parts p ON p.id = a.part_id
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE l.is_voided = 0 AND l.occurred_on >= :from AND l.occurred_on <= :to
        ORDER BY l.sequence, a.part_id, a.income_source_id
        """,
    )
    suspend fun attributions(from: String, to: String): List<OriginAttributionRecordRow>
}
