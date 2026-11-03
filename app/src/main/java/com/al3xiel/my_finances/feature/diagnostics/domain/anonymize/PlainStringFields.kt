package com.al3xiel.my_finances.feature.diagnostics.domain.anonymize

import com.al3xiel.my_finances.domain.model.event.EventCodec
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors

/**
 * Un texto plano del payload de un evento. `path` son los nombres JSON hasta el campo (`*` = valor de un mapa; las listas y los
 * subtipos de una jerarquía no agregan segmento); `key` es `Tipo.ruta`, la llave de [FieldClassification].
 */
data class PlainField(val key: String, val path: List<String>, val nullable: Boolean)

/**
 * Recorre el serializador de cada evento y devuelve sus `String` planos (D52). Los identificadores (`value class`), las fechas,
 * los enums y los números no son texto libre y no aparecen.
 */
object PlainStringFields {
    private val cache = HashMap<String, List<PlainField>>()

    fun of(type: String): List<PlainField> = synchronized(cache) {
        cache.getOrPut(type) {
            val found = ArrayList<PlainField>()
            walk(EventCodec.descriptor(type), emptyList(), found)
            merge(type, found)
        }
    }

    private fun walk(descriptor: SerialDescriptor, path: List<String>, out: MutableList<PlainField>) {
        if (descriptor.isInline) return
        when (descriptor.kind) {
            PrimitiveKind.STRING ->
                if (descriptor.serialName.removeSuffix("?") != "LocalDate") out += PlainField("", path, descriptor.isNullable)
            is PrimitiveKind -> Unit
            StructureKind.CLASS, StructureKind.OBJECT -> for (i in 0 until descriptor.elementsCount) {
                walk(descriptor.getElementDescriptor(i), path + descriptor.getElementName(i), out)
            }
            StructureKind.LIST -> walk(descriptor.getElementDescriptor(0), path, out)
            StructureKind.MAP -> walk(descriptor.getElementDescriptor(1), path + "*", out)
            PolymorphicKind.SEALED -> descriptor.getElementDescriptor(1).elementDescriptors.forEach { walk(it, path, out) }
            else -> Unit
        }
    }

    /** El mismo campo en varios subtipos cuenta una vez; es anulable solo si lo es en todos (así "[removed]" decodifica en todos). */
    private fun merge(type: String, found: List<PlainField>): List<PlainField> =
        found.groupBy { it.path }.map { (path, same) ->
            PlainField("$type.${path.joinToString(".")}", path, same.all { it.nullable })
        }
}
