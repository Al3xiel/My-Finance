package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.NewEvent
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Guarda lo que decide un caso de uso: una acción del usuario = una correlación. */
class EventRecorder @Inject constructor(
    private val eventStore: EventStore,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    suspend fun record(event: DomainEvent, occurredOn: LocalDate = today()) =
        record(listOf(NewEvent(event, occurredOn)))

    suspend fun record(events: List<NewEvent>) {
        eventStore.append(ids.newId(), events)
    }
}
