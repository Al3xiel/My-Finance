package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.RebuildReason
import javax.inject.Inject

/** Las versiones de los proyectores: la que quedó guardada en la base y la del código que corre. */
interface ProjectionVersions {
    val current: Map<String, Int>

    /** Solo los proyectores con estado guardado: vacío mientras ningún evento se haya guardado. */
    suspend fun stored(): Map<String, Int>
}

/**
 * Al arrancar, antes de cualquier evento nuevo (D32): si alguna versión guardada difiere de la del proyector (o falta) y
 * hay eventos, reconstruye todo con la razón `VERSION_CHANGE`, que a su vez re-verifica las conciliaciones resueltas.
 */
class ProjectionVersionGuard @Inject constructor(
    private val versions: ProjectionVersions,
    private val eventStore: EventStore,
    private val replay: ReplayProjectionsUseCase,
) {
    /** `true` si hizo el replay. */
    suspend operator fun invoke(): Boolean {
        if (versions.stored() == versions.current) return false
        if (eventStore.readAll().isEmpty()) return false
        replay(RebuildReason.VERSION_CHANGE)
        return true
    }
}
