package com.al3xiel.my_finances.feature.movements.data

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.movement.MovementDao
import com.al3xiel.my_finances.data.projection.movement.MovementEntity
import com.al3xiel.my_finances.data.projection.movement.MovementPartEntity
import com.al3xiel.my_finances.data.projection.movement.MovementPartTagEntity
import com.al3xiel.my_finances.data.projection.movement.MovementQueryDao
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.AggregateType
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import com.al3xiel.my_finances.feature.movements.domain.model.HistoryKind
import com.al3xiel.my_finances.feature.movements.domain.model.Movement
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDetail
import com.al3xiel.my_finances.feature.movements.domain.model.MovementFilter
import com.al3xiel.my_finances.feature.movements.domain.model.MovementHistoryEntry
import com.al3xiel.my_finances.feature.movements.domain.model.MovementPart
import com.al3xiel.my_finances.feature.movements.domain.model.MovementSummary
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class MovementRepositoryImpl @Inject constructor(
    private val movements: MovementDao,
    private val queries: MovementQueryDao,
    private val eventStore: EventStore,
) : MovementRepository {
    override suspend fun find(id: MovementId): Movement? =
        movements.movement(id.value)?.toDomain(queries.parts(id.value), queries.partTags(id.value))

    override suspend fun lastExpense(): Movement? =
        queries.lastExpense()?.let { it.toDomain(queries.parts(it.id), queries.partTags(it.id)) }

    override fun observeMovements(filter: MovementFilter): Flow<List<MovementSummary>> =
        queries.observeMovements(
            accountId = filter.accountId?.value,
            type = filter.type?.name,
            categoryId = filter.categoryId?.value,
            tagId = filter.tagId?.value,
            from = filter.from?.toString(),
            to = filter.to?.toString(),
            text = filter.text?.trim()?.takeIf { it.isNotEmpty() }?.let(::escapeLike),
        ).map { rows -> rows.map { it.toSummary() } }

    override fun observeDetail(id: MovementId): Flow<MovementDetail?> =
        combine(
            queries.observeMovement(id.value),
            queries.observeParts(id.value),
            queries.observePartTags(id.value),
        ) { movement, parts, tags -> movement?.toDomain(parts, tags) }
            .map { movement ->
                movement?.let { MovementDetail(it, history(id)) }
            }

    private suspend fun history(id: MovementId): List<MovementHistoryEntry> =
        eventStore.history(AggregateType.MOVEMENT, id.value).mapNotNull { it.toHistoryEntry() }

    private fun escapeLike(text: String) =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

private fun MovementEntity.toSummary() = MovementSummary(
    id = MovementId(id),
    type = type,
    accountId = AccountId(accountId),
    counterAccountId = counterAccountId?.let(::AccountId),
    amount = Money(amountMinor, currency),
    note = note,
    occurredOn = LocalDate.parse(occurredOn),
    status = status,
    revision = revision,
)

private fun MovementEntity.toDomain(parts: List<MovementPartEntity>, tags: List<MovementPartTagEntity>) = Movement(
    id = MovementId(id),
    type = type,
    accountId = AccountId(accountId),
    counterAccountId = counterAccountId?.let(::AccountId),
    amount = Money(amountMinor, currency),
    incomeSourceId = incomeSourceId?.let(::IncomeSourceId),
    note = note,
    occurredOn = LocalDate.parse(occurredOn),
    status = status,
    revision = revision,
    parts = parts.map { part ->
        MovementPart(
            id = PartId(part.id),
            amount = Money(part.amountMinor, currency),
            categoryId = CategoryId(part.categoryId),
            kind = part.kind,
            tagIds = tags.filter { it.partId == part.id }.map { TagId(it.tagId) },
        )
    },
)

private fun StoredEvent.toHistoryEntry(): MovementHistoryEntry? {
    val (kind, reason) = when (val e = event) {
        is IncomeRegistered, is ExpenseRegistered, is TransferRegistered -> HistoryKind.REGISTERED to null
        is MovementCorrected -> HistoryKind.CORRECTED to e.reason
        is MovementVoided -> HistoryKind.VOIDED to e.reason
        else -> return null
    }
    return MovementHistoryEntry(sequence, kind, occurredOn, recordedAt, reason)
}
