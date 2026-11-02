package com.al3xiel.my_finances.data.projection

import com.al3xiel.my_finances.domain.usecase.ProjectionVersions
import javax.inject.Inject

class ProjectionVersionsImpl @Inject constructor(
    private val projectors: @JvmSuppressWildcards Set<EventProjector>,
    private val states: ProjectionStateDao,
) : ProjectionVersions {
    override val current: Map<String, Int> get() = projectors.associate { it.name to it.version }

    /** Solo los proyectores con estado guardado; uno nuevo que falte hace que las mapas difieran. */
    override suspend fun stored(): Map<String, Int> =
        projectors.mapNotNull { p -> states.state(p.name)?.let { p.name to it.projectorVersion } }.toMap()
}
