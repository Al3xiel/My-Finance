package com.al3xiel.my_finances.data.projection.report

import androidx.room.Dao
import androidx.room.Query
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.PartKind

data class ReportPartRecord(
    val partId: String,
    val accountId: String,
    val categoryId: String,
    val parentId: String?,
    val amount: Long,
    val hasPool: Boolean,
    val reimbursed: Long,
)

data class ReportPartTagRecord(val partId: String, val tagId: String)

data class ReportPartDetailRecord(val partId: String, val occurredOn: String, val kind: PartKind, val note: String?)

data class ReportLedgerRecord(val accountId: String, val kind: LedgerEntryKind, val delta: Long)

/** Solo lecturas para los reportes (D35); no hay tablas propias. Las fechas son ISO (`yyyy-MM-dd`). */
@Dao
interface ReportQueryDao {
    /**
     * Partes de movimientos activos del periodo (de cualquier tipo: gasto, comisión, gasto no registrado). `reimbursed` es lo
     * asignado por reembolsos activos con fecha <= `closing`, no la columna `reimbursed_minor`.
     */
    @Query(
        """
        SELECT p.id AS partId, m.account_id AS accountId, p.category_id AS categoryId, c.parent_id AS parentId,
               p.amount_minor AS amount, (p.pool_id IS NOT NULL) AS hasPool,
               COALESCE((SELECT SUM(a.amount_minor)
                         FROM reimbursement_allocations a
                         JOIN reimbursements r ON r.id = a.reimbursement_id
                         JOIN movements rm ON rm.id = r.movement_id
                         WHERE a.part_id = p.id AND rm.status = 'ACTIVE' AND r.occurred_on <= :closing), 0) AS reimbursed
        FROM movement_parts p
        JOIN movements m ON m.id = p.movement_id
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE m.status = 'ACTIVE' AND m.occurred_on >= :from AND m.occurred_on <= :to
        ORDER BY p.id
        """,
    )
    suspend fun parts(from: String, to: String, closing: String): List<ReportPartRecord>

    @Query(
        """
        SELECT t.part_id AS partId, t.tag_id AS tagId
        FROM movement_part_tags t
        JOIN movement_parts p ON p.id = t.part_id
        JOIN movements m ON m.id = p.movement_id
        WHERE m.status = 'ACTIVE' AND m.occurred_on >= :from AND m.occurred_on <= :to
        ORDER BY t.part_id, t.tag_id
        """,
    )
    suspend fun partTags(from: String, to: String): List<ReportPartTagRecord>

    /** Fecha, clase y nota de cada parte del periodo: solo para la exportación (D38). */
    @Query(
        """
        SELECT p.id AS partId, m.occurred_on AS occurredOn, p.kind AS kind, m.note AS note
        FROM movement_parts p JOIN movements m ON m.id = p.movement_id
        WHERE m.status = 'ACTIVE' AND m.occurred_on >= :from AND m.occurred_on <= :to
        ORDER BY p.id
        """,
    )
    suspend fun partDetails(from: String, to: String): List<ReportPartDetailRecord>

    /** Asientos vigentes del periodo (los anulados o reemplazados quedan fuera). */
    @Query(
        """
        SELECT account_id AS accountId, entry_kind AS kind, delta_minor AS delta
        FROM ledger_entries
        WHERE is_voided = 0 AND occurred_on >= :from AND occurred_on <= :to
        ORDER BY sequence, id
        """,
    )
    suspend fun ledger(from: String, to: String): List<ReportLedgerRecord>
}
