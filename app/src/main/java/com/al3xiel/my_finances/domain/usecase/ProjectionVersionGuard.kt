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

/** `RestorePending` (docs/03): hay una restauración recién intercambiada cuyas proyecciones aún no se reconstruyeron. */
interface RestoreMarker {
    suspend fun isPending(): Boolean

    suspend fun mark()

    suspend fun clear()
}

/**
 * Al arrancar, antes de cualquier evento nuevo (D32): si alguna versión guardada difiere de la del proyector (o falta) y
 * hay eventos, reconstruye todo con la razón `VERSION_CHANGE`, que a su vez re-verifica las conciliaciones resueltas.
 */
class ProjectionVersionGuard @Inject constructor(
    private val versions: ProjectionVersions,
    private val eventStore: EventStore,
    private val replay: ReplayProjectionsUseCase,
    private val restoreMarker: RestoreMarker,
) {
    /** `true` si hizo el replay. Tras una restauración reconstruye con `RESTORE` aunque las versiones coincidan. */
    suspend operator fun invoke(): Boolean {
        val restored = restoreMarker.isPending()
        if (!restored && versions.stored() == versions.current) return false
        if (eventStore.readAll().isEmpty()) {
            if (restored) restoreMarker.clear()
            return false
        }
        replay(if (restored) RebuildReason.RESTORE else RebuildReason.VERSION_CHANGE)
        // Si el replay falla el marcador queda y se reintenta en el siguiente arranque.
        if (restored) restoreMarker.clear()
        return true
    }
}
