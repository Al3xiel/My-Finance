package com.al3xiel.my_finances.domain.model.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Sube el payload de una versión antigua a la siguiente; uno por (tipo, versión de origen). Vacío en la v1. */
fun interface Upcaster {
    fun upcast(payload: JsonObject): JsonObject
}

object UpcasterRegistry {
    private val upcasters = emptyMap<Pair<String, Int>, Upcaster>()

    fun upcast(type: String, schemaVersion: Int, payload: JsonObject): JsonObject {
        var version = schemaVersion
        var current = payload
        while (version < DomainEvent.CURRENT_SCHEMA_VERSION) {
            val step = upcasters[type to version] ?: error("no upcaster for $type v$version")
            current = step.upcast(current)
            version++
        }
        return current
    }
}

/** Texto del payload <-> evento. Determinista: mismo evento, mismo texto (el hash lo cubre tal como se guarda). */
object EventCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    private val serializers: Map<String, KSerializer<out DomainEvent>> = mapOf(
        "AccountCreated" to AccountCreated.serializer(),
        "AccountRenamed" to AccountRenamed.serializer(),
        "AccountArchived" to AccountArchived.serializer(),
        "CategoryCreated" to CategoryCreated.serializer(),
        "CategoryRenamed" to CategoryRenamed.serializer(),
        "CategoryArchived" to CategoryArchived.serializer(),
        "TagCreated" to TagCreated.serializer(),
        "TagRenamed" to TagRenamed.serializer(),
        "TagArchived" to TagArchived.serializer(),
        "PersonCreated" to PersonCreated.serializer(),
        "PersonRenamed" to PersonRenamed.serializer(),
        "PersonArchived" to PersonArchived.serializer(),
        "IncomeSourceCreated" to IncomeSourceCreated.serializer(),
        "IncomeRegistered" to IncomeRegistered.serializer(),
        "ExpenseRegistered" to ExpenseRegistered.serializer(),
        "TransferRegistered" to TransferRegistered.serializer(),
        "MovementCorrected" to MovementCorrected.serializer(),
        "MovementVoided" to MovementVoided.serializer(),
        "ProjectionsRebuilt" to ProjectionsRebuilt.serializer(),
    )

    val types: Set<String> get() = serializers.keys

    @Suppress("UNCHECKED_CAST")
    fun encode(event: DomainEvent): String =
        json.encodeToString(serializerFor(event.type) as KSerializer<DomainEvent>, event)

    fun decode(type: String, schemaVersion: Int, payload: String): DomainEvent {
        val upcast = UpcasterRegistry.upcast(type, schemaVersion, json.parseToJsonElement(payload) as JsonObject)
        return json.decodeFromJsonElement(serializerFor(type), upcast)
    }

    private fun serializerFor(type: String): KSerializer<out DomainEvent> =
        serializers[type] ?: error("unknown event type: $type")
}
