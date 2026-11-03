package com.al3xiel.my_finances.feature.diagnostics.domain.anonymize

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Lee los textos de un payload siguiendo una ruta de [PlainField] (`*` = valor de un mapa; las listas no agregan segmento). */
object PayloadTexts {
    fun read(payload: String, path: List<String>): List<String> {
        val found = ArrayList<String>()
        collect(Json.parseToJsonElement(payload), path, found)
        return found
    }

    private fun collect(element: JsonElement, path: List<String>, out: MutableList<String>) {
        when {
            path.isEmpty() -> if (element is JsonPrimitive && element !is JsonNull && element.isString) out += element.content
            element is JsonArray -> element.forEach { collect(it, path, out) }
            element is JsonObject && path.first() == "*" -> element.values.forEach { collect(it, path.drop(1), out) }
            element is JsonObject -> element[path.first()]?.let { collect(it, path.drop(1), out) }
        }
    }
}
