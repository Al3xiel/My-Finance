package com.al3xiel.my_finances.domain.model.event

enum class AggregateType { ACCOUNT, CATEGORY, TAG, PERSON, INCOME_SOURCE, MOVEMENT, POOL, LOAN, SYSTEM }

/** Hecho inmutable. `type` es la columna `events.type`; el payload es el resto de los campos. */
sealed interface DomainEvent {
    val type: String
    val aggregateType: AggregateType
    val aggregateId: String
    val schemaVersion: Int get() = CURRENT_SCHEMA_VERSION

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
