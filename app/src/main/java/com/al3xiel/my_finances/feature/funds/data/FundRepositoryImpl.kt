package com.al3xiel.my_finances.feature.funds.data

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.fund.FundCountEntity
import com.al3xiel.my_finances.data.projection.fund.FundEntity
import com.al3xiel.my_finances.data.projection.fund.FundQueryDao
import com.al3xiel.my_finances.data.projection.fund.FundRow
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.feature.funds.domain.model.Fund
import com.al3xiel.my_finances.feature.funds.domain.model.FundCountRecord
import com.al3xiel.my_finances.feature.funds.domain.model.FundEditFacts
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.model.FundSummary
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.rules.FundRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

class FundRepositoryImpl @Inject constructor(
    private val queries: FundQueryDao,
) : FundRepository {
    override suspend fun find(id: FundId): FundOverview? = queries.fund(id.value)?.toOverview()

    override fun observeFunds(): Flow<List<FundOverview>> = queries.observeFunds().map { rows -> rows.map { it.toOverview() } }

    override fun observeFund(id: FundId): Flow<FundOverview?> = queries.observeFund(id.value).map { it?.toOverview() }

    override fun observeSummary(): Flow<FundSummary> =
        combine(observeFunds(), queries.observeTotalBalance()) { funds, balance ->
            val reserved = funds.sumOf { it.reserved.amountMinor }
            FundSummary(pen(reserved), pen(balance - reserved))
        }

    override suspend fun availableBalanceMinor(): Long = observeSummary().first().available.amountMinor

    override suspend fun activeNames(): List<String> = queries.activeNames()

    override suspend fun toppedUpFrom(incomeMovementId: MovementId): Money = pen(queries.toppedUpFrom(incomeMovementId.value))

    override suspend fun editFacts(id: FundId) = FundEditFacts(
        hasUsageDays = queries.usageDayCount(id.value) > 0,
        hasCounts = queries.countCount(id.value) > 0,
        hasAssignedExpenses = queries.assignedExpenseCount(id.value) > 0,
        latestActivityOn = queries.latestActivityOn(id.value)?.let(LocalDate::parse),
    )

    override suspend fun usageDays(id: FundId): Set<LocalDate> = queries.usageDays(id.value).map(LocalDate::parse).toSet()

    override fun observeUsageDays(id: FundId): Flow<List<LocalDate>> = queries.observeUsageDays(id.value).map { it.map(LocalDate::parse) }

    override fun observeCounts(id: FundId): Flow<List<FundCountRecord>> = queries.observeCounts(id.value).map { rows -> rows.map { it.toDomain() } }
}

private fun FundRow.toOverview(): FundOverview {
    val domain = fund.toDomain()
    val detailedSpent = pen(detailed)
    val estimate = domain.dailyEstimate
    val reserved =
        if (domain.status == FundStatus.CLOSED) pen(0) else FundRules.reserved(domain.allocated, detailedSpent, domain.untrackedSpent)
    return FundOverview(
        fund = domain,
        detailedSpent = detailedSpent,
        usageDays = usageDays,
        lastCountOn = lastCountOn?.let(LocalDate::parse),
        reserved = reserved,
        estimatedSpent = FundRules.estimatedSpent(domain.mode, usageDays, estimate),
        estimatedRemaining = FundRules.estimatedRemaining(domain.mode, domain.allocated, detailedSpent, domain.untrackedSpent, usageDays, estimate),
    )
}

private fun FundEntity.toDomain() = Fund(
    id = FundId(id),
    name = name,
    mode = consumptionMode,
    dailyEstimate = dailyEstimateMinor?.let(::pen),
    startsOn = LocalDate.parse(startsOn),
    endsOn = endsOn?.let(LocalDate::parse),
    allocated = pen(allocatedMinor),
    untrackedSpent = pen(untrackedSpentMinor),
    status = status,
    remainderPolicy = remainderPolicy,
    rolloverToFundId = rolloverToFundId?.let(::FundId),
    closedOn = closedOn?.let(LocalDate::parse),
)

private fun FundCountEntity.toDomain() = FundCountRecord(
    declaredRemaining = pen(declaredRemainingMinor),
    realSpent = pen(realSpentMinor),
    estimatedSpent = estimatedSpentMinor?.let(::pen),
    deviation = deviationMinor?.let(::pen),
    occurredOn = LocalDate.parse(occurredOn),
)
