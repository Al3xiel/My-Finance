package com.al3xiel.my_finances.feature.home.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.feature.accounts.domain.FakeAccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.feature.backup.domain.FakeBackupSettings
import com.al3xiel.my_finances.feature.backup.domain.usecase.ObserveBackupStatusUseCase
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.FakeExpectedIncomeRepository
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.home.domain.usecase.ObserveHomeSummaryUseCase
import com.al3xiel.my_finances.feature.pools.domain.model.PoolDetail
import com.al3xiel.my_finances.feature.pools.domain.model.Receivables
import com.al3xiel.my_finances.feature.pools.domain.repository.ReceivablesRepository
import com.al3xiel.my_finances.feature.privacy.FakeSecuritySettingsRepository
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import com.al3xiel.my_finances.feature.reconciliation.domain.FakeReconciliationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    private val accounts = FakeAccountRepository()
    private val settings = FakeSecuritySettingsRepository(SecuritySettings(hideAmounts = false))
    private val receivables = object : ReceivablesRepository {
        override fun observeReceivables(): Flow<Receivables> =
            flowOf(Receivables(Money.zero(CurrencyCode.PEN), emptyList(), emptyList(), emptyList()))
        override fun observePoolDetail(poolId: com.al3xiel.my_finances.domain.model.PoolId): Flow<PoolDetail?> = flowOf(null)
    }

    private fun viewModel() = HomeViewModel(
        ObserveHomeSummaryUseCase(
            accounts, FakeFundRepository(), receivables, FakeExpectedIncomeRepository(), FakeReconciliationRepository(),
            ObserveBackupStatusUseCase(FakeEventStore(), FakeBackupSettings(), clock), clock,
        ),
        settings,
    )

    @Test
    fun showsTheSummaryOfWhatTheProjectionsSay() {
        val id = AccountId("bbva")
        accounts.accounts[id] = Account(id, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, false)
        accounts.balances[id] = 12_300

        val state = viewModel().uiState.value

        assertNotNull(state.summary)
        assertEquals(12_300L, state.summary!!.accountsBalance.amountMinor)
        assertEquals(listOf("bbva"), state.summary!!.accounts.map { it.account.id.value })
    }

    @Test
    fun theEyeFlipsAndPersistsTheGlobalSetting() {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.hideAmounts)

        viewModel.onAction(HomeUiAction.ToggleHideAmounts)

        assertTrue(settings.state.value.hideAmounts)
        assertTrue(viewModel.uiState.value.hideAmounts)

        viewModel.onAction(HomeUiAction.ToggleHideAmounts)

        assertFalse(settings.state.value.hideAmounts)
        assertFalse(viewModel.uiState.value.hideAmounts)
    }
}
