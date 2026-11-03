package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.domain.model.event.EntityRef
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.FieldClassification
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.PayloadTexts
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.PlainStringFields
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.Treatment
import javax.inject.Inject

/**
 * El alias real de cada seudónimo, tal como era al exportar (04 §3.3.2): se resuelve con los eventos hasta `exportedUpToSequence`
 * y gana el último nombre de la entidad en ese tramo. Un renombre posterior no se filtra al diccionario.
 */
class ResolveAliasesUseCase @Inject constructor(private val eventStore: EventStore) {
    suspend operator fun invoke(refs: Map<String, EntityRef>, upToSequence: Long): Map<String, String> {
        val wanted = refs.values.map { it.entityId }.toSet()
        val latest = HashMap<Pair<String, com.al3xiel.my_finances.domain.model.event.EntityType>, String>()
        for (stored in eventStore.readAll()) {
            if (stored.sequence > upToSequence || stored.event.aggregateId !in wanted) continue
            for (field in PlainStringFields.of(stored.event.type)) {
                val rule = FieldClassification.rules[field.key] ?: continue
                val type = rule.entityType
                if (rule.treatment != Treatment.PSEUDONYM || type == null) continue
                PayloadTexts.read(stored.payload, field.path).lastOrNull()?.let { latest[stored.event.aggregateId to type] = it }
            }
        }
        return refs.mapNotNull { (pseudonym, ref) -> latest[ref.entityId to ref.entityType]?.let { pseudonym to it } }.toMap()
    }
}
