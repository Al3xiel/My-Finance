package com.al3xiel.my_finances.feature.reconciliation.data

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.movement.MovementEntity
import com.al3xiel.my_finances.data.projection.reconciliation.ReconciliationEntity
import com.al3xiel.my_finances.data.projection.reconciliation.ReconciliationQueryDao
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import com.al3xiel.my_finances.feature.reconciliation.domain.model.AdjustmentInfo
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationDetail
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationMovement
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class ReconciliationRepositoryImpl @Inject constructor(
    private val queries: ReconciliationQueryDao,
) : ReconciliationRepository {
    override suspend fun find(id: ReconciliationId): Reconciliation? = queries.reconciliation(id.value)?.toDomain()

    override suspend fun openFor(accountId: AccountId): Reconciliation? = queries.openFor(accountId.value)?.toDomain()

    override suspend fun remainingDifference(id: ReconciliationId): Money? {
        val row = queries.reconciliation(id.value) ?: return null
        val computed = queries.computedMinor(row.id, row.accountId, row.checkedSequence)
        return ReconciliationRules.remainingDifference(Money(row.realMinor, row.currency), Money(computed, row.currency))
    }

    override suspend fun isLinked(id: ReconciliationId, movementId: MovementId): Boolean =
        queries.isLinked(id.value, movementId.value)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDetail(id: ReconciliationId): Flow<ReconciliationDetail?> =
        queries.observeReconciliation(id.value).flatMapLatest { row ->
            if (row == null) {
                flowOf(null)
            } else {
                combine(
                    queries.observeComputedMinor(row.id, row.accountId, row.checkedSequence),
                    queries.observeLinked(row.id),
                    queries.observeSuggestions(row.id),
                ) { computedMinor, linked, suggestions ->
                    val computed = Money(computedMinor, row.currency)
                    ReconciliationDetail(
                        reconciliation = row.toDomain(),
                        computed = computed,
                        difference = ReconciliationRules.remainingDifference(Money(row.realMinor, row.currency), computed),
                        linked = linked.map { it.toDomain() },
                        suggestions = suggestions.map { it.toDomain() },
                    )
                }
            }
        }

    override fun observeLatest(accountId: AccountId): Flow<Reconciliation?> =
        queries.observeLatest(accountId.value).map { it?.toDomain() }

    override fun observeNeedsReviewCount(): Flow<Int> = queries.observeNeedsReviewCount()
}

private fun ReconciliationEntity.toDomain() = Reconciliation(
    id = ReconciliationId(id),
    accountId = AccountId(accountId),
    real = Money(realMinor, currency),
    snapshotComputed = Money(snapshotComputedMinor, currency),
    status = status,
    cause = cause,
    adjustment = adjustmentMovementId?.let { movementId ->
        val minor = adjustmentMinor ?: 0L
        val direction = if (minor >= 0) AdjustmentDirection.UNIDENTIFIED_INCOME else AdjustmentDirection.UNIDENTIFIED_EXPENSE
        AdjustmentInfo(MovementId(movementId), direction, Money(Math.abs(minor), currency))
    },
    cancelReason = cancelReason,
    checkedAt = Instant.parse(checkedAt),
    resolvedAt = resolvedAt?.let(Instant::parse),
    needsReview = needsReview,
)

private fun MovementEntity.toDomain() = ReconciliationMovement(
    id = MovementId(id),
    type = type,
    accountId = AccountId(accountId),
    counterAccountId = counterAccountId?.let(::AccountId),
    amount = Money(amountMinor, currency),
    occurredOn = LocalDate.parse(occurredOn),
    note = note,
    status = status,
)
