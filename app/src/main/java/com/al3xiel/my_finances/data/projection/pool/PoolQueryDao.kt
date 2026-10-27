package com.al3xiel.my_finances.data.projection.pool

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.al3xiel.my_finances.data.projection.catalog.PersonEntity
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import kotlinx.coroutines.flow.Flow

data class PendingPartRow(
    @ColumnInfo(name = "part_id") val partId: String,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
    @ColumnInfo(name = "created_sequence") val createdSequence: Long,
    val pending: Long,
)

data class ReimbursedPartRow(
    @ColumnInfo(name = "part_id") val partId: String,
    @ColumnInfo(name = "pool_id") val poolId: String,
    val reimbursed: Long,
)

data class PendingPartLineRow(
    @ColumnInfo(name = "part_id") val partId: String,
    @ColumnInfo(name = "pool_id") val poolId: String,
    @ColumnInfo(name = "pool_name") val poolName: String,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
    @ColumnInfo(name = "created_sequence") val createdSequence: Long,
    @ColumnInfo(name = "category_name") val categoryName: String,
    val pending: Long,
)

data class StatementPartRow(
    @ColumnInfo(name = "part_id") val partId: String,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
    @ColumnInfo(name = "created_sequence") val createdSequence: Long,
    @ColumnInfo(name = "category_name") val categoryName: String,
    val amount: Long,
    val pending: Long,
)

data class PartTagRow(
    @ColumnInfo(name = "part_id") val partId: String,
    @ColumnInfo(name = "tag_name") val tagName: String,
)

data class PoolPendingRow(
    @ColumnInfo(name = "pool_id") val poolId: String,
    val name: String,
    val kind: PoolKind,
    @ColumnInfo(name = "person_id") val personId: String,
    val alias: String,
    @ColumnInfo(name = "period_pending") val periodPending: Long,
    @ColumnInfo(name = "total_pending") val totalPending: Long,
)

data class OpenLoanRow(
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "person_id") val personId: String,
    val alias: String,
    @ColumnInfo(name = "principal_minor") val principalMinor: Long,
    @ColumnInfo(name = "repaid_minor") val repaidMinor: Long,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
    val status: LoanStatus,
)

/**
 * Lecturas de cúmulos, periodos, reembolsos y por cobrar. Solo cuentan las partes con periodo asignado: anular un gasto
 * le quita el periodo (ver `PoolProjector`), así que las anuladas nunca suman.
 */
@Dao
interface PoolQueryDao {
    @Query("SELECT * FROM pools WHERE id = :id") suspend fun pool(id: String): PoolEntity?
    @Query("SELECT * FROM pools WHERE id = :id") fun observePool(id: String): Flow<PoolEntity?>

    @Query("SELECT name FROM pools WHERE is_archived = 0") suspend fun activeNames(): List<String>

    @Query("SELECT * FROM pools ORDER BY name") fun observePools(): Flow<List<PoolEntity>>

    @Query("SELECT COALESCE(SUM(amount_minor - reimbursed_minor), 0) FROM movement_parts WHERE pool_id = :poolId AND pool_period_id IS NOT NULL")
    suspend fun pendingMinor(poolId: String): Long

    @Query("SELECT id AS part_id, pool_id, reimbursed_minor AS reimbursed FROM movement_parts WHERE movement_id = :movementId AND reimbursed_minor > 0 AND pool_id IS NOT NULL")
    suspend fun reimbursedParts(movementId: String): List<ReimbursedPartRow>

    @Query(
        """
        SELECT p.id AS part_id, m.occurred_on AS occurred_on, p.created_sequence AS created_sequence,
               p.amount_minor - p.reimbursed_minor AS pending
        FROM movement_parts p
        JOIN movements m ON m.id = p.movement_id
        JOIN pools pl ON pl.id = p.pool_id
        WHERE pl.responsible_person_id = :personId AND p.pool_period_id IS NOT NULL
          AND p.amount_minor > p.reimbursed_minor AND (:poolId IS NULL OR p.pool_id = :poolId)
        ORDER BY m.occurred_on, p.created_sequence
        """,
    )
    suspend fun pendingParts(personId: String, poolId: String?): List<PendingPartRow>

    @Query(
        """
        SELECT p.id AS part_id, pl.id AS pool_id, pl.name AS pool_name, m.occurred_on AS occurred_on,
               p.created_sequence AS created_sequence, c.name AS category_name, p.amount_minor - p.reimbursed_minor AS pending
        FROM movement_parts p
        JOIN movements m ON m.id = p.movement_id
        JOIN pools pl ON pl.id = p.pool_id
        JOIN categories c ON c.id = p.category_id
        WHERE pl.responsible_person_id = :personId AND p.pool_period_id IS NOT NULL
          AND p.amount_minor > p.reimbursed_minor AND (:poolId IS NULL OR p.pool_id = :poolId)
        ORDER BY m.occurred_on, p.created_sequence
        """,
    )
    suspend fun pendingPartLines(personId: String, poolId: String?): List<PendingPartLineRow>

    @Query(
        """
        SELECT pt.part_id AS part_id, t.name AS tag_name
        FROM movement_part_tags pt JOIN tags t ON t.id = pt.tag_id
        JOIN movement_parts p ON p.id = pt.part_id JOIN pools pl ON pl.id = p.pool_id
        WHERE pl.responsible_person_id = :personId AND p.pool_period_id IS NOT NULL
          AND p.amount_minor > p.reimbursed_minor AND (:poolId IS NULL OR p.pool_id = :poolId)
        ORDER BY t.name
        """,
    )
    suspend fun pendingPartTags(personId: String, poolId: String?): List<PartTagRow>

    @Query("SELECT * FROM pool_periods WHERE pool_id = :poolId AND closed_on IS NULL ORDER BY opened_on DESC, rowid DESC LIMIT 1")
    suspend fun currentPeriod(poolId: String): PoolPeriodEntity?

    @Query(
        """
        SELECT p.id AS part_id, m.occurred_on AS occurred_on, p.created_sequence AS created_sequence,
               c.name AS category_name, p.amount_minor AS amount, p.amount_minor - p.reimbursed_minor AS pending
        FROM movement_parts p
        JOIN movements m ON m.id = p.movement_id
        JOIN categories c ON c.id = p.category_id
        WHERE p.pool_period_id = :periodId
        ORDER BY m.occurred_on, p.created_sequence
        """,
    )
    suspend fun periodParts(periodId: String): List<StatementPartRow>

    @Query(
        """
        SELECT pt.part_id AS part_id, t.name AS tag_name
        FROM movement_part_tags pt JOIN tags t ON t.id = pt.tag_id
        JOIN movement_parts p ON p.id = pt.part_id
        WHERE p.pool_period_id = :periodId
        ORDER BY t.name
        """,
    )
    suspend fun periodPartTags(periodId: String): List<PartTagRow>

    /** Lo pendiente en los periodos ya cerrados del cúmulo de `periodId`, sin contar ese periodo. */
    @Query(
        """
        SELECT COALESCE(SUM(p.amount_minor - p.reimbursed_minor), 0)
        FROM movement_parts p JOIN pool_periods pp ON pp.id = p.pool_period_id
        WHERE pp.pool_id = (SELECT pool_id FROM pool_periods WHERE id = :periodId)
          AND pp.closed_on IS NOT NULL AND pp.id != :periodId
        """,
    )
    suspend fun pendingBefore(periodId: String): Long

    @Query("SELECT * FROM pool_periods WHERE pool_id = :poolId ORDER BY opened_on DESC, rowid DESC")
    fun observePeriods(poolId: String): Flow<List<PoolPeriodEntity>>

    @Query(POOL_PENDING + " WHERE pl.is_archived = 0 GROUP BY pl.id ORDER BY pl.name")
    fun observePoolsPending(): Flow<List<PoolPendingRow>>

    @Query(POOL_PENDING + " WHERE pl.id = :poolId GROUP BY pl.id")
    fun observePoolPending(poolId: String): Flow<PoolPendingRow?>

    @Query(
        """
        SELECT l.id AS loan_id, l.person_id AS person_id, pe.alias AS alias, l.principal_minor AS principal_minor,
               l.repaid_minor AS repaid_minor, l.occurred_on AS occurred_on, l.status AS status
        FROM loans l
        JOIN movements m ON m.id = l.movement_id AND m.status = 'ACTIVE'
        JOIN people pe ON pe.id = l.person_id
        WHERE l.status = 'OPEN'
        ORDER BY l.occurred_on, l.id
        """,
    )
    fun observeOpenLoans(): Flow<List<OpenLoanRow>>

    private companion object {
        const val POOL_PENDING = """
            SELECT pl.id AS pool_id, pl.name AS name, pl.kind AS kind, pl.responsible_person_id AS person_id, pe.alias AS alias,
                   COALESCE(SUM(CASE WHEN pp.closed_on IS NULL THEN p.amount_minor - p.reimbursed_minor ELSE 0 END), 0) AS period_pending,
                   COALESCE(SUM(p.amount_minor - p.reimbursed_minor), 0) AS total_pending
            FROM pools pl
            JOIN people pe ON pe.id = pl.responsible_person_id
            LEFT JOIN movement_parts p ON p.pool_id = pl.id AND p.pool_period_id IS NOT NULL
            LEFT JOIN pool_periods pp ON pp.id = p.pool_period_id
        """
    }
}
