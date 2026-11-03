package com.al3xiel.my_finances.feature.diagnostics.domain.anonymize

import com.al3xiel.my_finances.domain.model.event.EntityRef
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** El evento tal como viaja: el registro original (con su hash) y el payload ya reescrito. */
data class AnonymizedEvent(val source: StoredEvent, val payload: String)

/** Un evento cuyo payload cambió: su hash original queda en el manifiesto y los campos que se tocaron. */
data class AlteredEvent(val sequence: Long, val originalHash: String, val fields: List<String>)

data class AnonymizationResult(
    val events: List<AnonymizedEvent>,
    val pseudonymMap: Map<String, EntityRef>,
    val altered: List<AlteredEvent>,
)

/**
 * Reescribe el payload de cada evento según [FieldClassification] (D52): reemplaza por seudónimo lo que identifica a una
 * entidad y elimina notas y motivos. Montos, fechas, IDs y orden no cambian. Un texto sin clasificar detiene todo: falla
 * cerrado antes que dejar pasar un nombre.
 */
class EventAnonymizer @Inject constructor() {
    private val json = Json

    fun anonymize(events: List<StoredEvent>): AnonymizationResult {
        val allocator = PseudonymAllocator()
        val out = ArrayList<AnonymizedEvent>(events.size)
        val altered = ArrayList<AlteredEvent>()
        for (stored in events) {
            var element = json.parseToJsonElement(stored.payload)
            val changed = ArrayList<String>()
            for (field in PlainStringFields.of(stored.event.type)) {
                val rule = FieldClassification.rules[field.key] ?: error("unclassified text field: ${field.key}")
                if (rule.treatment == Treatment.KEEP) continue
                val next = rewrite(element, field.path) { value ->
                    when {
                        value !is JsonPrimitive || value is JsonNull -> value
                        rule.treatment == Treatment.PSEUDONYM -> JsonPrimitive(allocator.pseudonymFor(EntityRef(requireNotNull(rule.entityType), stored.event.aggregateId)))
                        field.nullable -> JsonNull
                        else -> JsonPrimitive(REMOVED)
                    }
                }
                if (next != element) {
                    changed += field.key
                    element = next
                }
            }
            if (changed.isEmpty()) {
                out += AnonymizedEvent(stored, stored.payload)
            } else {
                out += AnonymizedEvent(stored, json.encodeToString(JsonElement.serializer(), element))
                altered += AlteredEvent(stored.sequence, stored.hash, changed)
            }
        }
        return AnonymizationResult(out, allocator.pseudonymMap(), altered)
    }

    private fun rewrite(element: JsonElement, path: List<String>, transform: (JsonElement) -> JsonElement): JsonElement = when {
        path.isEmpty() -> transform(element)
        element is JsonArray -> JsonArray(element.map { rewrite(it, path, transform) })
        element is JsonObject && path.first() == "*" -> JsonObject(element.mapValues { rewrite(it.value, path.drop(1), transform) })
        element is JsonObject -> element[path.first()]?.let { child ->
            JsonObject(element.toMutableMap().also { it[path.first()] = rewrite(child, path.drop(1), transform) })
        } ?: element
        else -> element
    }

    companion object {
        const val REMOVED = "[removed]"
    }
}
