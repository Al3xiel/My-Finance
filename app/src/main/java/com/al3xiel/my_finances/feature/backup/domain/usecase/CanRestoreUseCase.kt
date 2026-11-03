package com.al3xiel.my_finances.feature.backup.domain.usecase

import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.EventStore
import javax.inject.Inject

/** Restaurar exige una base sin datos propios (D44): solo eventos de sembrado y `ProjectionsRebuilt`. */
class CanRestoreUseCase @Inject constructor(
    private val eventStore: EventStore,
) {
    suspend operator fun invoke(): Boolean = eventStore.countNonSeed(
        SystemCatalog.incomeSources.map { it.id.value },
        SystemCatalog.defaultCategories.map { it.id.value },
    ) == 0L
}
