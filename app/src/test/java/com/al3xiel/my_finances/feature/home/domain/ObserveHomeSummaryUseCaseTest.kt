package com.al3xiel.my_finances.feature.home.domain

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.feature.accounts.domain.FakeAccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.backup.domain.FakeBackupSettings
import com.al3xiel.my_finances.feature.backup.domain.usecase.ObserveBackupStatusUseCase
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.FakeExpectedIncomeRepository
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.home.domain.model.HomeAlert
import com.al3xiel.my_finances.feature.home.domain.usecase.ObserveHomeSummaryUseCase
import com.al3xiel.my_finances.feature.pools.domain.model.Receivables
import com.al3xiel.my_finances.feature.pools.domain.repository.ReceivablesRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.FakeReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ObserveHomeSummaryUseCaseTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val accounts = FakeAccountRepository()
    private val funds = FakeFundRepository()
    private val expected = FakeExpectedIncomeRepository()
    private val reconciliations = FakeReconciliationRepository()
    private val store = FakeEventStore()
    private var receivable = 0L
    private val receivables = object : ReceivablesRepository {
        override fun observeReceivables(): Flow<Receivables> = flowOf(Receivables(pen(receivable), emptyList(), emptyList(), emptyList()))
        override fun observePoolDetail(poolId: com.al3xiel.my_finances.domain.model.PoolId) = throw UnsupportedOperationException()
    }
    private val bbva = AccountId("bbva")
    private val cash = AccountId("cash")
    private val source = IncomeSourceId("salary")

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun summary() = ObserveHomeSummaryUseCase(
        accounts, funds, receivables, expected, reconciliations,
        ObserveBackupStatusUseCase(store, FakeBackupSettings(intervalDays = 15), clock), clock,
    )

    private fun account(id: AccountId, balance: Long, archived: Boolean = false) {
        accounts.accounts[id] = Account(id, id.value, AccountType.BANK, CurrencyCode.PEN, null, archived)
        accounts.balances[id] = balance
    }

    @Test
    fun withoutAccountsEverythingIsZeroAndEmpty() = runTest {
        val home = summary()().first()

        assertEquals(pen(0), home.accountsBalance)
        assertEquals(pen(0), home.reserved)
        assertEquals(pen(0), home.freeAvailable)
        assertEquals(pen(0), home.receivable)
        assertEquals(emptyList<Any>(), home.accounts)
        assertEquals(emptyList<Any>(), home.funds)
        assertNull(home.nextIncome)
        assertEquals(emptyList<HomeAlert>(), home.alerts)
    }

    @Test
    fun theFourIndicatorsFollowTheFormulas() = runTest {
        account(bbva, 80_000)
        account(cash, 20_000)
        funds.put(FundId("f1"), allocated = 30_000, detailed = 10_000, untracked = 5_000)
        receivable = 12_345

        val home = summary()().first()

        assertEquals(pen(100_000), home.accountsBalance)
        assertEquals(pen(15_000), home.reserved)
        assertEquals(pen(85_000), home.freeAvailable)
        assertEquals(pen(12_345), home.receivable)
        assertEquals(listOf("bbva", "cash"), home.accounts.map { it.account.id.value })
    }

    @Test
    fun freeAvailableCanBeNegativeAndIsNotClamped() = runTest {
        account(bbva, 10_000)
        funds.put(FundId("f1"), allocated = 30_000)

        assertEquals(pen(-20_000), summary()().first().freeAvailable)
    }

    @Test
    fun aClosedFundNeitherReservesNorAppears() = runTest {
        account(bbva, 50_000)
        funds.put(FundId("open"), allocated = 10_000)
        funds.put(FundId("closed"), allocated = 99_000, status = FundStatus.CLOSED)

        val home = summary()().first()

        assertEquals(pen(10_000), home.reserved)
        assertEquals(listOf("open"), home.funds.map { it.fund.id.value })
    }

    @Test
    fun anArchivedAccountHasNoRowButStillCountsInTheBalance() = runTest {
        account(bbva, 70_000)
        account(cash, 5_000, archived = true)

        val home = summary()().first()

        assertEquals(pen(75_000), home.accountsBalance)
        assertEquals(listOf("bbva"), home.accounts.map { it.account.id.value })
    }

    @Test
    fun theNextIncomeIsTheOldestOverdueOneEvenIfAScheduledIsCloser() = runTest {
        expected.put("scheduled", bbva, source, from = today.plusDays(1), to = today.plusDays(5))
        expected.put("late", bbva, source, from = today.minusDays(10), to = today.minusDays(3), status = ExpectedIncomeStatus.OVERDUE)
        expected.put("later", bbva, source, from = today.minusDays(4), to = today.minusDays(1), status = ExpectedIncomeStatus.OVERDUE)

        val home = summary()().first()

        assertEquals("late", home.nextIncome!!.id.value)
        assertEquals(listOf(HomeAlert.OverdueIncome(home.nextIncome!!.id)), home.alerts)
    }

    @Test
    fun withoutOverdueTheClosestScheduledByStartWinsAndConfirmedOrCancelledAreIgnored() = runTest {
        expected.put("far", bbva, source, from = today.plusDays(20), to = today.plusDays(25))
        expected.put("near", bbva, source, from = today.plusDays(2), to = today.plusDays(8))
        expected.put("done", bbva, source, from = today, to = today.plusDays(1), status = ExpectedIncomeStatus.CONFIRMED)
        expected.put("off", bbva, source, from = today, to = today.plusDays(1), status = ExpectedIncomeStatus.CANCELLED)

        val home = summary()().first()

        assertEquals("near", home.nextIncome!!.id.value)
        assertEquals(emptyList<HomeAlert>(), home.alerts)
    }

    @Test
    fun theOverdueRuleUsesTheInjectedClockNotTheStoredStatus() = runTest {
        expected.put("stale", bbva, source, from = today.minusDays(6), to = today.minusDays(2), status = ExpectedIncomeStatus.SCHEDULED)

        val home = summary()().first()

        assertEquals("stale", home.nextIncome!!.id.value)
        assertEquals(listOf<HomeAlert>(HomeAlert.OverdueIncome(home.nextIncome!!.id)), home.alerts)
    }

    @Test
    fun anIncomeBecomesOverdueWhenTheDayChangesWithoutAnyProjectionChange() = runTest {
        val start = today.atTime(23, 0).toInstant(ZoneOffset.UTC)
        val ticking = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
            override fun instant(): Instant = start.plusMillis(testScheduler.currentTime)
        }
        expected.put("ends-today", bbva, source, from = today.minusDays(3), to = today)
        val emissions = mutableListOf<List<HomeAlert>>()
        backgroundScope.launch {
            ObserveHomeSummaryUseCase(
                accounts, funds, receivables, expected, reconciliations,
                ObserveBackupStatusUseCase(store, FakeBackupSettings(intervalDays = 15), ticking), ticking,
            )().collect { emissions += it.alerts }
        }
        runCurrent()
        assertEquals(listOf(emptyList<HomeAlert>()), emissions)

        advanceTimeBy(Duration.ofHours(2).toMillis())
        runCurrent()

        assertEquals(HomeAlert.OverdueIncome::class, emissions.last().single()::class)
    }

    @Test
    fun alertsComeOrderedAndCappedAtThree() = runTest {
        reconciliations.needsReviewId = ReconciliationId("rv")
        reconciliations.openId = ReconciliationId("op")
        expected.put("late", bbva, source, from = today.minusDays(10), to = today.minusDays(3), status = ExpectedIncomeStatus.OVERDUE)
        store.append("c", listOf(NewEvent(AccountRenamed(bbva, "x"), today.minusDays(40))))

        val alerts = summary()().first().alerts

        assertEquals(3, alerts.size)
        assertEquals(HomeAlert.NeedsReview(ReconciliationId("rv")), alerts[0])
        assertEquals(HomeAlert.OpenReconciliation(ReconciliationId("op")), alerts[1])
        assertEquals(HomeAlert.OverdueIncome::class, alerts[2]::class)
    }
}
