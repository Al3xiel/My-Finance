package com.al3xiel.my_finances.feature.funds.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.feature.funds.domain.model.Fund
import com.al3xiel.my_finances.feature.funds.domain.model.FundCountRecord
import com.al3xiel.my_finances.feature.funds.domain.model.FundEditFacts
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.model.FundSummary
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.rules.FundRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

class FakeFundRepository : FundRepository {
    val funds = linkedMapOf<FundId, FundOverview>()
    val facts = linkedMapOf<FundId, FundEditFacts>()
    val usage = linkedMapOf<FundId, Set<LocalDate>>()
    val toppedUp = linkedMapOf<MovementId, Long>()
    var available = 100_000L

    override suspend fun find(id: FundId) = funds[id]
    override fun observeFunds(): Flow<List<FundOverview>> = flowOf(funds.values.toList())
    override fun observeFund(id: FundId): Flow<FundOverview?> = flowOf(funds[id])
    override fun observeSummary(): Flow<FundSummary> = flowOf(FundSummary(pen(0), pen(available)))
    override suspend fun availableBalanceMinor() = available
    override suspend fun activeNames() = funds.values.filter { it.fund.status == FundStatus.ACTIVE }.map { it.fund.name }
    override suspend fun toppedUpFrom(incomeMovementId: MovementId) = pen(toppedUp[incomeMovementId] ?: 0L)
    override suspend fun editFacts(id: FundId) = facts[id] ?: FundEditFacts(hasUsageDays = false, hasCounts = false, hasAssignedExpenses = false)
    override suspend fun usageDays(id: FundId) = usage[id].orEmpty()
    override fun observeUsageDays(id: FundId): Flow<List<LocalDate>> = flowOf(usage[id].orEmpty().sorted())
    override fun observeCounts(id: FundId): Flow<List<FundCountRecord>> = flowOf(emptyList())

    /** Un fondo con `allocated`, `detailed` y `untracked` ya cargados; el resto sale de las mismas reglas que usa el repositorio real. */
    fun put(
        id: FundId,
        name: String = id.value,
        mode: ConsumptionMode = ConsumptionMode.DETAILED,
        dailyEstimate: Long? = null,
        startsOn: LocalDate = LocalDate.of(2026, 10, 1),
        endsOn: LocalDate? = null,
        allocated: Long = 0,
        detailed: Long = 0,
        untracked: Long = 0,
        status: FundStatus = FundStatus.ACTIVE,
        lastCountOn: LocalDate? = null,
        usageDays: Int = 0,
    ): FundOverview {
        val fund = Fund(
            id, name, mode, dailyEstimate?.let(::pen), startsOn, endsOn, pen(allocated), pen(untracked), status, null, null, null,
        )
        val overview = FundOverview(
            fund = fund,
            detailedSpent = pen(detailed),
            usageDays = usageDays,
            lastCountOn = lastCountOn,
            reserved = if (status == FundStatus.CLOSED) pen(0) else FundRules.reserved(pen(allocated), pen(detailed), pen(untracked)),
            estimatedSpent = FundRules.estimatedSpent(mode, usageDays, dailyEstimate?.let(::pen)),
            estimatedRemaining = pen(allocated - detailed - untracked),
        )
        funds[id] = overview
        return overview
    }

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
}
