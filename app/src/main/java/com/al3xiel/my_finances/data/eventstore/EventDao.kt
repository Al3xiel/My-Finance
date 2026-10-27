package com.al3xiel.my_finances.data.eventstore

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    @Query("SELECT hash FROM events ORDER BY sequence DESC LIMIT 1")
    suspend fun lastHash(): String?

    @Query("SELECT * FROM events ORDER BY sequence")
    suspend fun all(): List<EventEntity>

    @Query("SELECT * FROM events WHERE sequence > :sequence ORDER BY sequence")
    suspend fun after(sequence: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE aggregate_type = :aggregateType AND aggregate_id = :aggregateId ORDER BY sequence")
    suspend fun history(aggregateType: String, aggregateId: String): List<EventEntity>
}
