package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.event.AggregateType
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.EventHasher
import com.al3xiel.my_finances.domain.model.event.HashedFields
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import java.time.Instant

/** Registro en memoria con la misma cadena de hashes que el real; los proyectores se prueban aparte. */
class FakeEventStore : EventStore {
    val events = mutableListOf<StoredEvent>()
    private var tick = 1_000L

    override suspend fun append(correlationId: String, events: List<NewEvent>): List<StoredEvent> =
        events.map { new ->
            val payload = EventCodec.encode(new.event)
            val previous = this.events.lastOrNull()?.hash ?: EventHasher.GENESIS
            val recordedAt = Instant.ofEpochMilli(tick++)
            val eventId = "e${this.events.size + 1}"
            val hash = EventHasher.hash(
                HashedFields(
                    previousHash = previous,
                    eventId = eventId,
                    type = new.event.type,
                    schemaVersion = new.event.schemaVersion,
                    aggregateType = new.event.aggregateType.name,
                    aggregateId = new.event.aggregateId,
                    correlationId = correlationId,
                    payload = payload,
                    occurredOn = new.occurredOn.toString(),
                    recordedAt = recordedAt.toEpochMilli(),
                    appVersion = "test",
                ),
            )
            StoredEvent(
                sequence = this.events.size + 1L,
                eventId = eventId,
                schemaVersion = new.event.schemaVersion,
                correlationId = correlationId,
                payload = payload,
                occurredOn = new.occurredOn,
                recordedAt = recordedAt,
                appVersion = "test",
                previousHash = previous,
                hash = hash,
                event = new.event,
            ).also { this.events += it }
        }

    override suspend fun readAll(): List<StoredEvent> = events.toList()

    override suspend fun readAfter(sequence: Long): List<StoredEvent> = events.filter { it.sequence > sequence }

    override suspend fun history(aggregateType: AggregateType, aggregateId: String): List<StoredEvent> =
        events.filter { it.event.aggregateType == aggregateType && it.event.aggregateId == aggregateId }
}
