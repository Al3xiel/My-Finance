package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.ProjectionsRebuilt
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Reconstruye todas las proyecciones y deja constancia con `ProjectionsRebuilt`. */
class ReplayProjectionsUseCase @Inject constructor(
    private val eventStore: EventStore,
    private val rebuilder: ProjectionRebuilder,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(reason: String) {
        val versions = rebuilder.rebuild()
        eventStore.append(ids.newId(), listOf(NewEvent(ProjectionsRebuilt(versions, reason), LocalDate.now(clock))))
    }
}
