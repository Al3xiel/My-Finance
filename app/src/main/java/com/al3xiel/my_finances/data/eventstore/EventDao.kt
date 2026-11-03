package com.al3xiel.my_finances.data.eventstore

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    /** Solo para la importación a la base de staging: conserva `sequence`, `recorded_at` y los hashes de la fila. */
    @Insert
    suspend fun insertRaw(events: List<EventEntity>)

    @Query(
        "SELECT MIN(recorded_at) AS firstEventAt, " +
            "MAX(CASE WHEN type IN ('BackupCreated', 'BackupRestored') THEN recorded_at END) AS lastBackupAt FROM events",
    )
    fun observeBackupTimes(): Flow<BackupTimesRow>

    @Query("SELECT hash FROM events ORDER BY sequence DESC LIMIT 1")
    suspend fun lastHash(): String?

    /** Lectura por páginas para el respaldo y la verificación: nunca el registro entero en memoria. */
    @Query("SELECT * FROM events WHERE sequence > :afterSequence ORDER BY sequence LIMIT :limit")
    suspend fun page(afterSequence: Long, limit: Int): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Long

    @Query("SELECT MIN(recorded_at) FROM events")
    suspend fun firstEventAt(): Long?

    @Query("SELECT MAX(recorded_at) FROM events WHERE type IN ('BackupCreated', 'BackupRestored')")
    suspend fun lastBackupAt(): Long?

    /** Eventos que no son de sembrado: 0 = base sin datos propios (D44). Los ids del sembrado los pasa el llamador. */
    @Query(
        "SELECT COUNT(*) FROM events WHERE NOT (type = 'ProjectionsRebuilt' " +
            "OR (type = 'IncomeSourceCreated' AND aggregate_id IN (:sourceIds)) " +
            "OR (type = 'CategoryCreated' AND aggregate_id IN (:categoryIds)))",
    )
    suspend fun countNonSeed(sourceIds: List<String>, categoryIds: List<String>): Long

    @Query("SELECT * FROM events ORDER BY sequence")
    suspend fun all(): List<EventEntity>

    @Query("SELECT * FROM events WHERE sequence > :sequence ORDER BY sequence")
    suspend fun after(sequence: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE aggregate_type = :aggregateType AND aggregate_id = :aggregateId ORDER BY sequence")
    suspend fun history(aggregateType: String, aggregateId: String): List<EventEntity>
}

data class BackupTimesRow(val firstEventAt: Long?, val lastBackupAt: Long?)
