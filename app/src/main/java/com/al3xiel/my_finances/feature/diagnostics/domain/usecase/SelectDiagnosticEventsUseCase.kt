package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.event.AggregateType
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.FieldClassification
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.PlainStringFields
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.Treatment
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSelection
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSelectionRequest
import com.al3xiel.my_finances.feature.diagnostics.domain.model.SnapshotCut
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Elige los eventos de un paquete de diagnóstico (04 §3.3.1) y arma la vista previa. Un evento "toca" la cuenta si su payload
 * menciona su id, así que la selección sigue las referencias sin conocer cada tipo de evento: los catálogos mencionados
 * (cuentas, categorías, conceptos, personas, fuentes, cúmulos, fondos) entran aunque estén lejos, y sus propias referencias también.
 */
class SelectDiagnosticEventsUseCase @Inject constructor(private val eventStore: EventStore) {

    suspend operator fun invoke(request: DiagnosticSelectionRequest): Outcome<DiagnosticSelection> = attempt {
        val all = eventStore.readAll()
        val candidates = all.filter { it.event.type !in auditTypes }
        val mentions = Mentions()

        val upTo = all.lastOrNull()?.sequence ?: 0L
        val chosen: Chosen = when (request.scope) {
            DiagnosticScope.FULL -> Chosen(null, null, candidates, SnapshotCut(upTo))
            DiagnosticScope.RECONCILIATION -> {
                val id = request.reconciliationId ?: return@attempt Outcome.Failed(BusinessError.NotFound)
                val check = candidates.firstOrNull { (it.event as? BalanceCheckRecorded)?.reconciliationId == id }
                    ?: return@attempt Outcome.Failed(BusinessError.NotFound)
                val account = (check.event as BalanceCheckRecorded).accountId
                val selected = forAccount(candidates, mentions, account, check.sequence, setOf(id.value))
                Chosen(account, id, selected.events, SnapshotCut(check.sequence, selected.linkedMovements))
            }
            DiagnosticScope.ACCOUNT_HISTORY -> {
                val fromReconciliation = request.reconciliationId?.let { id ->
                    candidates.firstOrNull { (it.event as? BalanceCheckRecorded)?.reconciliationId == id }
                        ?: return@attempt Outcome.Failed(BusinessError.NotFound)
                }
                val account = request.accountId ?: (fromReconciliation?.event as? BalanceCheckRecorded)?.accountId
                    ?: return@attempt Outcome.Failed(BusinessError.NotFound)
                if (candidates.none { it.event.type == "AccountCreated" && it.event.aggregateId == account.value }) {
                    return@attempt Outcome.Failed(BusinessError.NotFound)
                }
                val reconciliations = candidates.mapNotNull { it.event as? BalanceCheckRecorded }
                    .filter { it.accountId == account }.map { it.reconciliationId.value }.toSet()
                Chosen(account, request.reconciliationId, forAccount(candidates, mentions, account, null, reconciliations).events, SnapshotCut(upTo))
            }
        }

        val events = withReferencedCatalogs(candidates, mentions, chosen.events).sortedBy { it.sequence }
        Outcome.Ok(
            DiagnosticSelection(
                scope = request.scope,
                accountId = chosen.account,
                reconciliationId = chosen.reconciliationId,
                events = events,
                exportedUpToSequence = upTo,
                firstOccurredOn = events.minOfOrNull { it.occurredOn },
                lastOccurredOn = events.maxOfOrNull { it.occurredOn },
                anonymizedFields = anonymizedFields(events),
                cut = chosen.cut,
            ),
        )
    }

    private class Chosen(val account: AccountId?, val reconciliationId: ReconciliationId?, val events: List<StoredEvent>, val cut: SnapshotCut)

    private class Selected(val events: List<StoredEvent>, val linkedMovements: Set<String>)

    /**
     * Lo que toca la cuenta (hasta `cutoff` si lo hay), los movimientos de esos eventos (correcciones y anulaciones no mencionan
     * la cuenta), y por cada conciliación sus eventos y los movimientos vinculados, aunque sean posteriores al chequeo.
     */
    private fun forAccount(
        candidates: List<StoredEvent>,
        mentions: Mentions,
        account: AccountId,
        cutoff: Long?,
        reconciliations: Set<String>,
    ): Selected {
        fun inRange(e: StoredEvent) = cutoff == null || e.sequence <= cutoff
        val touching = candidates.filter { e ->
            inRange(e) && account.value in mentions.of(e) &&
                (e.event.aggregateType != AggregateType.RECONCILIATION || e.event.aggregateId in reconciliations)
        }
        val reconciliationEvents = candidates.filter { it.event.aggregateType == AggregateType.RECONCILIATION && it.event.aggregateId in reconciliations }
        val linkedMovements = candidates
            .filter { it.event.aggregateType == AggregateType.MOVEMENT && reconciliations.any { id -> id in mentions.of(it) } }
            .map { it.event.aggregateId }.toSet()
        val movementIds = touching.filter { it.event.aggregateType == AggregateType.MOVEMENT }.map { it.event.aggregateId }.toSet() + linkedMovements
        val movementEvents = candidates.filter {
            it.event.aggregateType == AggregateType.MOVEMENT && it.event.aggregateId in movementIds &&
                (inRange(it) || it.event.aggregateId in linkedMovements)
        }
        return Selected((touching + reconciliationEvents + movementEvents).distinctBy { it.sequence }, linkedMovements)
    }

    /** Suma los eventos de catálogo de cada id mencionado, y repite con lo que esos catálogos mencionan (un concepto apunta a una persona). */
    private fun withReferencedCatalogs(candidates: List<StoredEvent>, mentions: Mentions, base: List<StoredEvent>): List<StoredEvent> {
        val catalog = candidates.filter { it.event.type in catalogTypes }.groupBy { it.event.aggregateId }
        val selected = base.associateBy { it.sequence }.toMutableMap()
        var pending = base
        while (pending.isNotEmpty()) {
            val added = pending.flatMap { mentions.of(it) }.toSet().flatMap { catalog[it].orEmpty() }.filter { it.sequence !in selected }
            added.forEach { selected[it.sequence] = it }
            pending = added
        }
        return selected.values.toList()
    }

    private fun anonymizedFields(events: List<StoredEvent>): List<String> = events.map { it.event.type }.distinct()
        .flatMap { PlainStringFields.of(it) }.map { it.key }
        .filter { FieldClassification.rules[it]?.treatment != Treatment.KEEP }
        .distinct().sorted()

    /** Cada texto de un payload (ids incluidos), calculado una vez por evento. */
    private class Mentions {
        private val cache = HashMap<Long, Set<String>>()

        fun of(event: StoredEvent): Set<String> = cache.getOrPut(event.sequence) {
            val found = HashSet<String>()
            collect(Json.parseToJsonElement(event.payload), found)
            found
        }

        private fun collect(element: JsonElement, out: MutableSet<String>) {
            when (element) {
                is JsonObject -> element.values.forEach { collect(it, out) }
                is JsonArray -> element.forEach { collect(it, out) }
                is JsonPrimitive -> if (element.isString) out += element.content
            }
        }
    }

    private companion object {
        val auditTypes = setOf("BackupCreated", "BackupRestored", "DiagnosticExportCreated", "DiagnosticDictionaryExported")
        val catalogTypes = setOf(
            "AccountCreated", "AccountRenamed", "AccountArchived",
            "CategoryCreated", "CategoryRenamed", "CategoryArchived",
            "TagCreated", "TagRenamed", "TagArchived",
            "PersonCreated", "PersonRenamed", "PersonArchived",
            "IncomeSourceCreated",
            "ReimbursementPoolCreated", "ReimbursementPoolRenamed", "ReimbursementPoolArchived",
            "FundCreated", "FundEdited",
        )
    }
}
