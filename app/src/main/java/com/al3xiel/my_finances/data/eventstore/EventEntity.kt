package com.al3xiel.my_finances.data.eventstore

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Fila de `events`: solo se inserta (el DAO no tiene update ni delete y los triggers lo impiden). */
@Entity(
    tableName = "events",
    indices = [
        Index(value = ["event_id"], unique = true),
        Index(value = ["aggregate_type", "aggregate_id", "sequence"]),
        Index(value = ["type"]),
        Index(value = ["occurred_on"]),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    @ColumnInfo(name = "event_id") val eventId: String,
    val type: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "correlation_id") val correlationId: String,
    val payload: String,
    @ColumnInfo(name = "occurred_on") val occurredOn: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "app_version") val appVersion: String,
    @ColumnInfo(name = "previous_hash") val previousHash: String,
    val hash: String,
)
