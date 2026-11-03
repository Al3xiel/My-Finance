package com.al3xiel.my_finances.feature.diagnostics.data

import com.al3xiel.my_finances.core.id.UuidIdGenerator
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.projection.ProjectionRebuilderImpl
import com.al3xiel.my_finances.data.projection.ProjectorSetFactory
import java.time.Clock
import javax.inject.Inject
import javax.inject.Named

/**
 * Reconstruye las proyecciones de una base con el conjunto completo de proyectores (D50). Solo reproduce los eventos que ya
 * están en ella: no agrega ninguno ni encadena hashes. `database` es el espacio de diagnóstico, nunca la base real.
 */
class WorkspaceProjector @Inject constructor(
    private val clock: Clock,
    @Named("appVersion") private val appVersion: String,
) {
    /** Devuelve las versiones de proyector con las que se reconstruyó. */
    suspend fun replay(database: AppDatabase): Map<String, Int> {
        val projectors = ProjectorSetFactory(database).create()
        val store = EventStoreImpl(database, projectors, UuidIdGenerator(), clock, appVersion)
        return ProjectionRebuilderImpl(database, projectors, store, clock).rebuild()
    }
}
