package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.feature.diagnostics.domain.model.FingerprintChange
import com.al3xiel.my_finances.feature.diagnostics.domain.model.RawEventLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.RawEventPage
import com.al3xiel.my_finances.feature.diagnostics.domain.model.TableFingerprint
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.TableFingerprintSource
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Huellas de las tablas de proyección: con ellas S73 muestra qué cambió al re-proyectar. */
class TableFingerprintUseCase @Inject constructor(private val source: TableFingerprintSource) {
    suspend fun snapshot(): List<TableFingerprint> = source.fingerprints().sortedBy { it.table }

    fun compare(before: List<TableFingerprint>, after: List<TableFingerprint>): List<FingerprintChange> {
        val left = before.associateBy { it.table }
        val right = after.associateBy { it.table }
        return (left.keys + right.keys).sorted().map { table ->
            FingerprintChange(table, left[table]?.rows, right[table]?.rows, changed = left[table] != right[table])
        }
    }
}

/** Eventos crudos de a [PAGE_SIZE], sin decodificar: nunca se carga el registro entero. */
class ReadRawEventsUseCase @Inject constructor(private val eventStore: EventStore) {
    suspend operator fun invoke(afterSequence: Long): RawEventPage {
        val rows = eventStore.readChainPage(afterSequence, PAGE_SIZE + 1)
        return RawEventPage(
            items = rows.take(PAGE_SIZE).map {
                val f = it.fields
                RawEventLine(it.sequence, f.type, f.aggregateType, f.aggregateId, f.occurredOn, f.payload)
            },
            hasMore = rows.size > PAGE_SIZE,
        )
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}

class ObserveNeedsReviewCountUseCase @Inject constructor(private val reconciliations: ReconciliationRepository) {
    operator fun invoke(): Flow<Int> = reconciliations.observeNeedsReviewCount()
}
