package com.al3xiel.my_finances.data.projection

import androidx.room.withTransaction
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.usecase.ProjectionRebuilder
import java.time.Clock
import javax.inject.Inject

class ProjectionRebuilderImpl @Inject constructor(
    private val database: AppDatabase,
    private val projectors: @JvmSuppressWildcards Set<EventProjector>,
    private val eventStore: EventStore,
    private val clock: Clock,
) : ProjectionRebuilder {
    override suspend fun rebuild(): Map<String, Int> = database.withTransaction {
        val events = eventStore.readAll()
        val rebuiltAt = clock.millis()
        val ordered = projectors.sortedBy { it.order }
        ordered.forEach { projector ->
            projector.reset()
            events.forEach { projector.apply(it) }
            database.projectionStateDao().upsert(
                ProjectionStateEntity(projector.name, projector.version, events.lastOrNull()?.sequence ?: 0, rebuiltAt),
            )
        }
        projectors.associate { it.name to it.version }
    }
}
