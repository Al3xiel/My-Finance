package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.AggregateType
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.NewEvent
import javax.inject.Inject

/** Siembra las fuentes de ingreso del sistema y las categorías por defecto; solo crea lo que falta. */
class SeedCatalogUseCase @Inject constructor(
    private val recorder: EventRecorder,
    private val eventStore: EventStore,
) {
    suspend operator fun invoke() {
        val today = recorder.today()
        val missing = buildList {
            SystemCatalog.incomeSources
                .filter { eventStore.history(AggregateType.INCOME_SOURCE, it.id.value).isEmpty() }
                .forEach { add(NewEvent(IncomeSourceCreated(it.id, it.name, null), today)) }
            SystemCatalog.defaultCategories
                .filter { eventStore.history(AggregateType.CATEGORY, it.id.value).isEmpty() }
                .forEach { add(NewEvent(CategoryCreated(it.id, it.name, it.icon, it.color, null), today)) }
        }
        if (missing.isNotEmpty()) recorder.record(missing)
    }
}
