package com.al3xiel.my_finances.data.projection.movement

import androidx.room.Dao
import androidx.room.Query
import com.al3xiel.my_finances.feature.pools.domain.rules.ReimbursementStatus
import kotlinx.coroutines.flow.Flow

/** Una parte con cúmulo y lo que se le reembolsó; las columnas salen de `movement_parts` y `pools`. */
data class PartReimbursementRow(
    val partId: String,
    val poolId: String,
    val poolName: String,
    val amountMinor: Long,
    val reimbursedMinor: Long,
    val status: ReimbursementStatus,
)

/** Lecturas de movimientos para repositorios y pantallas; el proyector no las usa. */
@Dao
interface MovementQueryDao {
    /** Cada filtro nulo se ignora. `text` ya viene con `%`, `_` y `\` escapados. */
    @Query(
        """
        SELECT * FROM movements
        WHERE (:accountId IS NULL OR account_id = :accountId OR counter_account_id = :accountId)
          AND (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR id IN (
                SELECT movement_id FROM movement_parts
                WHERE category_id = :categoryId OR (:useCategorySet = 1 AND category_id IN (:categoryIds))))
          AND (:tagId IS NULL OR id IN (
                SELECT p.movement_id FROM movement_parts p
                JOIN movement_part_tags t ON t.part_id = p.id WHERE t.tag_id = :tagId))
          AND (:from IS NULL OR occurred_on >= :from)
          AND (:to IS NULL OR occurred_on <= :to)
          AND (:text IS NULL OR note LIKE '%' || :text || '%' ESCAPE '\')
        ORDER BY occurred_on DESC, last_sequence DESC, id
        """,
    )
    fun observeMovements(
        accountId: String?,
        type: String?,
        categoryId: String?,
        useCategorySet: Boolean,
        categoryIds: List<String>,
        tagId: String?,
        from: String?,
        to: String?,
        text: String?,
    ): Flow<List<MovementEntity>>

    @Query("SELECT * FROM movements WHERE type = 'EXPENSE' AND status = 'ACTIVE' ORDER BY last_sequence DESC LIMIT 1")
    suspend fun lastExpense(): MovementEntity?

    @Query("SELECT * FROM movements WHERE id = :id") fun observeMovement(id: String): Flow<MovementEntity?>

    @Query("SELECT * FROM movement_parts WHERE movement_id = :id ORDER BY rowid")
    fun observeParts(id: String): Flow<List<MovementPartEntity>>

    @Query("SELECT * FROM movement_parts WHERE movement_id = :id ORDER BY rowid")
    suspend fun parts(id: String): List<MovementPartEntity>

    @Query(
        "SELECT t.* FROM movement_part_tags t JOIN movement_parts p ON p.id = t.part_id WHERE p.movement_id = :id",
    )
    fun observePartTags(id: String): Flow<List<MovementPartTagEntity>>

    @Query(
        "SELECT t.* FROM movement_part_tags t JOIN movement_parts p ON p.id = t.part_id WHERE p.movement_id = :id",
    )
    suspend fun partTags(id: String): List<MovementPartTagEntity>

    @Query(
        """
        SELECT p.id AS partId, p.pool_id AS poolId, pl.name AS poolName, p.amount_minor AS amountMinor,
               p.reimbursed_minor AS reimbursedMinor, p.reimbursement_status AS status
        FROM movement_parts p JOIN pools pl ON pl.id = p.pool_id
        WHERE p.movement_id = :id AND p.pool_id IS NOT NULL
        ORDER BY p.rowid
        """,
    )
    fun observePartReimbursements(id: String): Flow<List<PartReimbursementRow>>
}
