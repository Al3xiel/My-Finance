package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.domain.model.ReconciliationId
import kotlinx.serialization.Serializable

/** Cuánto del registro entra en un paquete de diagnóstico (04 §3.3.1). */
enum class DiagnosticScope { RECONCILIATION, ACCOUNT_HISTORY, FULL }

/** Cuánto revela un diccionario de seudónimos (04 §3.3.2). */
enum class DictionaryScope { PARTIAL, FULL }

/** Qué clase de entidad reemplaza un seudónimo. */
enum class EntityType { ACCOUNT, PERSON, TAG, CATEGORY, INCOME_SOURCE, POOL, FUND }

/** A qué entidad apunta un seudónimo; el alias real se resuelve después con los eventos (nunca se guarda aquí). */
@Serializable
data class EntityRef(val entityType: EntityType, val entityId: String)

/**
 * Se emite después de escribir y verificar el paquete (D53). Con conciliación pertenece a ella; si el origen es S73
 * pertenece al sistema y se identifica por `exportId`.
 */
@Serializable
data class DiagnosticExportCreated(
    val exportId: String,
    val reconciliationId: ReconciliationId?,
    val scope: DiagnosticScope,
    val anonymized: Boolean,
    val exportedUpToSequence: Long,
    val pseudonymMap: Map<String, EntityRef>,
    val eventCount: Long,
    val packageHash: String,
) : DomainEvent {
    override val type get() = "DiagnosticExportCreated"
    override val aggregateType get() = if (reconciliationId != null) AggregateType.RECONCILIATION else AggregateType.SYSTEM
    override val aggregateId get() = reconciliationId?.value ?: exportId
}

/** Pertenece a la conciliación que generó el paquete: el diccionario solo se extrae desde ella (DG7). */
@Serializable
data class DiagnosticDictionaryExported(
    val dictionaryId: String,
    val exportId: String,
    val reconciliationId: ReconciliationId,
    val scope: DictionaryScope,
    val revealedPseudonyms: List<String>,
    val revealedNoteEventIds: List<String>,
) : DomainEvent {
    override val type get() = "DiagnosticDictionaryExported"
    override val aggregateType get() = AggregateType.RECONCILIATION
    override val aggregateId get() = reconciliationId.value
}
