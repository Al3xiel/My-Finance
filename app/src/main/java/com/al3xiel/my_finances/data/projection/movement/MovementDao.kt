package com.al3xiel.my_finances.data.projection.movement

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

/** Escritura para el proyector; las consultas de pantalla vienen en T14. */
@Dao
interface MovementDao {
    @Upsert suspend fun upsertMovement(movement: MovementEntity)

    @Query("SELECT * FROM movements WHERE id = :id") suspend fun movement(id: String): MovementEntity?

    @Insert suspend fun insertParts(parts: List<MovementPartEntity>)

    @Insert suspend fun insertPartTags(tags: List<MovementPartTagEntity>)

    @Query("SELECT id FROM movement_parts WHERE movement_id = :movementId") suspend fun partIdsOf(movementId: String): List<String>

    /** Solo lo que es de este proyector: el resto de las columnas lo escriben los de cúmulos y reembolsos. */
    @Query("UPDATE movement_parts SET amount_minor = :amountMinor, category_id = :categoryId, pool_id = :poolId WHERE id = :id")
    suspend fun updatePartCore(id: String, amountMinor: Long, categoryId: String, poolId: String?)

    @Query("DELETE FROM movement_parts WHERE id IN (:ids)") suspend fun deletePartsById(ids: List<String>)

    @Query("DELETE FROM movement_part_tags WHERE part_id IN (SELECT id FROM movement_parts WHERE movement_id = :movementId)")
    suspend fun deletePartTagsOf(movementId: String)

    @Query("DELETE FROM movement_parts WHERE movement_id = :movementId") suspend fun deletePartsOf(movementId: String)

    @Query("DELETE FROM movements") suspend fun clearMovements()
    @Query("DELETE FROM movement_parts") suspend fun clearParts()
    @Query("DELETE FROM movement_part_tags") suspend fun clearPartTags()
}
