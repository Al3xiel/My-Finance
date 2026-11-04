package com.al3xiel.my_finances.domain.model.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonArray

/** Sube el payload de una versión antigua a la siguiente; uno por (tipo, versión de origen) que cambió en esa versión. */
fun interface Upcaster {
    fun upcast(payload: JsonObject): JsonObject
}

object UpcasterRegistry {
    /**
     * Solo los tipos cuyo payload cambió. La versión es una sola para todos los eventos: un tipo sin upcaster para ese paso no
     * cambió y su payload pasa igual. v1 → v2: las partes de un gasto ganan `note` (nulo), en el gasto y en la corrección.
     */
    private val upcasters: Map<Pair<String, Int>, Upcaster> = mapOf(
        ("ExpenseRegistered" to 1) to Upcaster { payload -> payload.withPartNotes() },
        ("MovementCorrected" to 1) to Upcaster { payload ->
            val snapshot = payload["snapshot"] as? JsonObject
            if (snapshot == null || (snapshot["type"] as? JsonPrimitive)?.content != "EXPENSE") {
                payload
            } else {
                JsonObject(payload + ("snapshot" to snapshot.withPartNotes()))
            }
        },
    )

    fun upcast(type: String, schemaVersion: Int, payload: JsonObject): JsonObject {
        var version = schemaVersion
        var current = payload
        while (version < DomainEvent.CURRENT_SCHEMA_VERSION) {
            current = upcasters[type to version]?.upcast(current) ?: current
            version++
        }
        return current
    }

    private fun JsonObject.withPartNotes(): JsonObject {
        val parts = this["parts"] as? JsonArray ?: return this
        val upgraded = parts.map { part -> if (part is JsonObject && "note" !in part) JsonObject(part + ("note" to JsonNull)) else part }
        return JsonObject(this + ("parts" to JsonArray(upgraded)))
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
        "ReimbursementPoolCreated" to ReimbursementPoolCreated.serializer(),
        "ReimbursementPoolRenamed" to ReimbursementPoolRenamed.serializer(),
        "ReimbursementPoolArchived" to ReimbursementPoolArchived.serializer(),
        "SettlementStatementGenerated" to SettlementStatementGenerated.serializer(),
        "ReimbursementReceived" to ReimbursementReceived.serializer(),
        "LoanGranted" to LoanGranted.serializer(),
        "LoanRepaymentReceived" to LoanRepaymentReceived.serializer(),
        "FundCreated" to FundCreated.serializer(),
        "FundEdited" to FundEdited.serializer(),
        "FundToppedUp" to FundToppedUp.serializer(),
        "ExpectedIncomeScheduled" to ExpectedIncomeScheduled.serializer(),
        "ExpectedIncomeEdited" to ExpectedIncomeEdited.serializer(),
        "ExpectedIncomeConfirmed" to ExpectedIncomeConfirmed.serializer(),
        "ExpectedIncomeCancelled" to ExpectedIncomeCancelled.serializer(),
        "UsageDayMarked" to UsageDayMarked.serializer(),
        "UsageDayUnmarked" to UsageDayUnmarked.serializer(),
        "FundCounted" to FundCounted.serializer(),
        "FundClosed" to FundClosed.serializer(),
        "BalanceCheckRecorded" to BalanceCheckRecorded.serializer(),
        "MovementLinkedToReconciliation" to MovementLinkedToReconciliation.serializer(),
        "ReconciliationResolved" to ReconciliationResolved.serializer(),
        "ReconciliationCancelled" to ReconciliationCancelled.serializer(),
        "ProjectionsRebuilt" to ProjectionsRebuilt.serializer(),
        "BackupCreated" to BackupCreated.serializer(),
        "BackupRestored" to BackupRestored.serializer(),
        "DiagnosticExportCreated" to DiagnosticExportCreated.serializer(),
        "DiagnosticDictionaryExported" to DiagnosticDictionaryExported.serializer(),
    )

    val types: Set<String> get() = serializers.keys

    @Suppress("UNCHECKED_CAST")
    fun encode(event: DomainEvent): String =
        json.encodeToString(serializerFor(event.type) as KSerializer<DomainEvent>, event)

    fun decode(type: String, schemaVersion: Int, payload: String): DomainEvent {
        val upcast = UpcasterRegistry.upcast(type, schemaVersion, json.parseToJsonElement(payload) as JsonObject)
        return json.decodeFromJsonElement(serializerFor(type), upcast)
    }

    /** Estructura del payload de un tipo: la usa el anonimizador para encontrar cada texto plano. */
    fun descriptor(type: String): SerialDescriptor = serializerFor(type).descriptor

    private fun serializerFor(type: String): KSerializer<out DomainEvent> =
        serializers[type] ?: error("unknown event type: $type")
}
