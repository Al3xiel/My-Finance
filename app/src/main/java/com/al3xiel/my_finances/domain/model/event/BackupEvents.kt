package com.al3xiel.my_finances.domain.model.event

import kotlinx.serialization.Serializable

/** Se emite al terminar y verificar un respaldo; no entra en el archivo que describe (D46). `lastHash` es el de la cadena respaldada. */
@Serializable
data class BackupCreated(val backupId: String, val eventCount: Long, val lastHash: String) : DomainEvent {
    override val type get() = "BackupCreated"
    override val aggregateType get() = AggregateType.BACKUP
    override val aggregateId get() = backupId
}

/** Lo agrega el importador encadenado al último hash del archivo restaurado (D46). */
@Serializable
data class BackupRestored(val backupId: String, val eventCount: Long) : DomainEvent {
    override val type get() = "BackupRestored"
    override val aggregateType get() = AggregateType.BACKUP
    override val aggregateId get() = backupId
}
