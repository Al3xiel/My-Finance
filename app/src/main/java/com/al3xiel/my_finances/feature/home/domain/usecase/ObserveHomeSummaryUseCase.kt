package com.al3xiel.my_finances.feature.home.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.backup.domain.usecase.ObserveBackupStatusUseCase
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeRules
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeStatusRules
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.home.domain.model.HomeAlert
import com.al3xiel.my_finances.feature.home.domain.model.HomeSummary
import com.al3xiel.my_finances.feature.home.domain.rules.HomeAlertRules
import com.al3xiel.my_finances.feature.pools.domain.repository.ReceivablesRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

/** Arma Inicio solo leyendo proyecciones; no escribe ni recalcula saldos (docs/04 §5.5, D56–D58). */
class ObserveHomeSummaryUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val funds: FundRepository,
    private val receivables: ReceivablesRepository,
    private val expectedIncomes: ExpectedIncomeRepository,
    private val reconciliations: ReconciliationRepository,
    private val backupStatus: ObserveBackupStatusUseCase,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<HomeSummary> {
        val money = combine(accounts.observeAccounts(), funds.observeFunds(), receivables.observeReceivables()) { accountList, fundList, owed ->
            Money3(accountList, fundList, owed.total)
        }
        val alertSources = combine(
            expectedIncomes.observeAll(),
            reconciliations.observeOldestNeedsReviewId(),
            reconciliations.observeOpenId(),
            backupStatus(),
        ) { incomes, needsReview, open, backup -> AlertSources(incomes, needsReview, open, backup.overdue) }
        return combine(money, alertSources, today()) { m, a, day -> build(m, a, day) }
    }

    /** Hoy según el reloj inyectado; vuelve a emitir al cambiar el día para que «vencido» no quede con el día anterior. */
    private fun today(): Flow<LocalDate> = flow {
        while (true) {
            val day = LocalDate.now(clock)
            emit(day)
            val nextMidnight = day.plusDays(1).atStartOfDay(clock.zone).toInstant()
            delay(Duration.between(clock.instant(), nextMidnight).toMillis().coerceAtLeast(MIN_TICK_MILLIS))
        }
    }

    private fun build(money: Money3, sources: AlertSources, today: LocalDate): HomeSummary {
        val zero = Money.zero(CurrencyCode.PEN)
        val balance = money.accounts.fold(zero) { sum, line -> sum + Money(line.balanceMinor, CurrencyCode.PEN) }
        val activeFunds = money.funds.filter { it.fund.status == FundStatus.ACTIVE }
        val reserved = activeFunds.fold(zero) { sum, fund -> sum + fund.reserved }
        val pending = sources.incomes.filter { ExpectedIncomeRules.isPending(it.status) && it.estimated.currency == CurrencyCode.PEN }
        val oldestOverdue = pending
            .filter { ExpectedIncomeStatusRules.isOverdue(it.range.to, today) }
            .minWithOrNull(compareBy<ExpectedIncome> { it.range.to }.thenBy { it.range.from }.thenBy { it.id.value })
        val scheduled = pending
            .filterNot { ExpectedIncomeStatusRules.isOverdue(it.range.to, today) }
            .minWithOrNull(compareBy<ExpectedIncome> { it.range.from }.thenBy { it.range.to }.thenBy { it.id.value })
        return HomeSummary(
            accountsBalance = balance,
            reserved = reserved,
            freeAvailable = balance - reserved,
            receivable = money.receivable,
            accounts = money.accounts.filterNot { it.account.isArchived },
            funds = activeFunds,
            nextIncome = oldestOverdue ?: scheduled,
            alerts = HomeAlertRules.build(sources.needsReviewId, sources.openId, oldestOverdue?.id, sources.backupOverdue),
        )
    }

    private companion object {
        const val MIN_TICK_MILLIS = 1_000L
    }

    private class Money3(val accounts: List<AccountWithBalance>, val funds: List<FundOverview>, val receivable: Money)

    private class AlertSources(
        val incomes: List<ExpectedIncome>,
        val needsReviewId: com.al3xiel.my_finances.domain.model.ReconciliationId?,
        val openId: com.al3xiel.my_finances.domain.model.ReconciliationId?,
        val backupOverdue: Boolean,
    )
}
