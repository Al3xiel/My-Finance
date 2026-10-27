package com.al3xiel.my_finances.data.projection.pool

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.al3xiel.my_finances.data.projection.catalog.PersonEntity
import kotlinx.coroutines.flow.Flow

data class ReimbursementRow(
    @ColumnInfo(name = "movement_id") val movementId: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
    val voided: Boolean,
)

data class PersonPartRow(
    @ColumnInfo(name = "part_id") val partId: String,
    @ColumnInfo(name = "pool_name") val poolName: String,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
    @ColumnInfo(name = "category_name") val categoryName: String,
    val amount: Long,
    val pending: Long,
)

/**
 * Lecturas del detalle de una persona (S35) y de su saldo. Como en [PoolQueryDao], solo cuentan las partes con periodo
 * asignado: anular un gasto le quita el periodo.
 */
@Dao
interface PersonQueryDao {
    @Query(
        """
        SELECT
          (SELECT COALESCE(SUM(p.amount_minor - p.reimbursed_minor), 0)
           FROM movement_parts p JOIN pools pl ON pl.id = p.pool_id
           WHERE pl.responsible_person_id = :personId AND pl.is_archived = 0 AND p.pool_period_id IS NOT NULL)
          +
          (SELECT COALESCE(SUM(l.principal_minor - l.repaid_minor), 0)
           FROM loans l JOIN movements m ON m.id = l.movement_id AND m.status = 'ACTIVE'
           WHERE l.person_id = :personId AND l.status = 'OPEN')
        """,
    )
    suspend fun balanceMinor(personId: String): Long

    @Query("SELECT * FROM people WHERE id = :id") fun observePerson(id: String): Flow<PersonEntity?>

    @Query(
        """
        SELECT r.movement_id AS movement_id, r.amount_minor AS amount_minor, r.occurred_on AS occurred_on,
               m.status = 'VOIDED' AS voided
        FROM reimbursements r JOIN movements m ON m.id = r.movement_id
        WHERE r.person_id = :personId
        ORDER BY r.occurred_on DESC, r.id DESC
        """,
    )
    fun observeReimbursements(personId: String): Flow<List<ReimbursementRow>>

    @Query(
        """
        SELECT p.id AS part_id, pl.name AS pool_name, m.occurred_on AS occurred_on, c.name AS category_name,
               p.amount_minor AS amount, p.amount_minor - p.reimbursed_minor AS pending
        FROM movement_parts p
        JOIN movements m ON m.id = p.movement_id
        JOIN pools pl ON pl.id = p.pool_id
        JOIN categories c ON c.id = p.category_id
        WHERE pl.responsible_person_id = :personId AND p.pool_period_id IS NOT NULL
        ORDER BY m.occurred_on DESC, p.created_sequence DESC
        """,
    )
    fun observePersonParts(personId: String): Flow<List<PersonPartRow>>

    @Query(
        """
        SELECT pt.part_id AS part_id, t.name AS tag_name
        FROM movement_part_tags pt JOIN tags t ON t.id = pt.tag_id
        JOIN movement_parts p ON p.id = pt.part_id JOIN pools pl ON pl.id = p.pool_id
        WHERE pl.responsible_person_id = :personId AND p.pool_period_id IS NOT NULL
        ORDER BY t.name
        """,
    )
    fun observePersonPartTags(personId: String): Flow<List<PartTagRow>>

    /** Deudas de la persona, abiertas o pagadas, sin las anuladas. */
    @Query(
        """
        SELECT l.id AS loan_id, l.person_id AS person_id, pe.alias AS alias, l.principal_minor AS principal_minor,
               l.repaid_minor AS repaid_minor, l.occurred_on AS occurred_on, l.status AS status
        FROM loans l
        JOIN movements m ON m.id = l.movement_id AND m.status = 'ACTIVE'
        JOIN people pe ON pe.id = l.person_id
        WHERE l.person_id = :personId
        ORDER BY l.occurred_on DESC, l.id
        """,
    )
    fun observePersonLoans(personId: String): Flow<List<OpenLoanRow>>
}
