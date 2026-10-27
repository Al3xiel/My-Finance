package com.al3xiel.my_finances.data.eventstore

import androidx.room.withTransaction
import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.ProjectionStateEntity
import com.al3xiel.my_finances.domain.model.event.AggregateType
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.EventHasher
import com.al3xiel.my_finances.domain.model.event.HashedFields
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class EventStoreImpl @Inject constructor(
    private val database: AppDatabase,
    private val projectors: @JvmSuppressWildcards Set<EventProjector>,
    private val ids: IdGenerator,
    private val clock: Clock,
    @Named("appVersion") private val appVersion: String,
) : EventStore {
    private val dao = database.eventDao()
    private val ordered = projectors.sortedBy { it.order }

    override suspend fun append(correlationId: String, events: List<NewEvent>): List<StoredEvent> =
        database.withTransaction {
            var previousHash = dao.lastHash() ?: EventHasher.GENESIS
            val appended = events.map { new ->
                val event = new.event
                val payload = EventCodec.encode(event)
                val recordedAt = Instant.now(clock)
                val eventId = ids.newId()
                val hash = EventHasher.hash(
                    HashedFields(
                        previousHash = previousHash,
                        eventId = eventId,
                        type = event.type,
                        schemaVersion = event.schemaVersion,
                        aggregateType = event.aggregateType.name,
                        aggregateId = event.aggregateId,
                        correlationId = correlationId,
                        payload = payload,
                        occurredOn = new.occurredOn.toString(),
                        recordedAt = recordedAt.toEpochMilli(),
                        appVersion = appVersion,
                    ),
                )
                val entity = EventEntity(
                    eventId = eventId,
                    type = event.type,
                    schemaVersion = event.schemaVersion,
                    aggregateType = event.aggregateType.name,
                    aggregateId = event.aggregateId,
                    correlationId = correlationId,
                    payload = payload,
                    occurredOn = new.occurredOn.toString(),
                    recordedAt = recordedAt.toEpochMilli(),
                    appVersion = appVersion,
                    previousHash = previousHash,
                    hash = hash,
                )
                val stored = entity.copy(sequence = dao.insert(entity)).toStored()
                ordered.forEach { it.apply(stored) }
                previousHash = hash
                stored
            }
            appended.lastOrNull()?.let { last -> recordProgress(last.sequence) }
            appended
        }

    private suspend fun recordProgress(lastSequence: Long) {
        val states = database.projectionStateDao()
        projectors.forEach { projector ->
            val rebuiltAt = states.state(projector.name)?.rebuiltAt
            states.upsert(ProjectionStateEntity(projector.name, projector.version, lastSequence, rebuiltAt))
        }
    }

    override suspend fun readAll(): List<StoredEvent> = dao.all().map { it.toStored() }

    override suspend fun readAfter(sequence: Long): List<StoredEvent> = dao.after(sequence).map { it.toStored() }

    override suspend fun history(aggregateType: AggregateType, aggregateId: String): List<StoredEvent> =
        dao.history(aggregateType.name, aggregateId).map { it.toStored() }

    private fun EventEntity.toStored() = StoredEvent(
        sequence = sequence,
        eventId = eventId,
        schemaVersion = schemaVersion,
        correlationId = correlationId,
        payload = payload,
        occurredOn = LocalDate.parse(occurredOn),
        recordedAt = Instant.ofEpochMilli(recordedAt),
        appVersion = appVersion,
        previousHash = previousHash,
        hash = hash,
        event = EventCodec.decode(type, schemaVersion, payload),
    )
}
