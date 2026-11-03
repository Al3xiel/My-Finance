package com.al3xiel.my_finances.feature.onboarding.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.feature.accounts.domain.FakeAccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.onboarding.domain.FakeOnboardingRepository
import com.al3xiel.my_finances.feature.onboarding.domain.model.OnboardingStep
import com.al3xiel.my_finances.feature.onboarding.domain.usecase.ResolveOnboardingStepUseCase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingGateViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val accounts = FakeAccountRepository()
    private val onboarding = FakeOnboardingRepository()

    private fun viewModel() = OnboardingGateViewModel(ResolveOnboardingStepUseCase(accounts, onboarding))

    private fun addAccount() {
        accounts.accounts[AccountId("a")] = Account(AccountId("a"), "BBVA", AccountType.BANK, CurrencyCode.PEN, null, false)
    }

    @Test
    fun freshInstallStartsAtWelcome() {
        assertEquals(OnboardingGateState.Flow(OnboardingStep.Welcome), viewModel().uiState.value)
    }

    @Test
    fun accountsWithoutFlagStartAtSecurity() {
        addAccount()
        assertEquals(OnboardingGateState.Flow(OnboardingStep.Security), viewModel().uiState.value)
    }

    @Test
    fun flagGoesStraightToTheApp() {
        onboarding.flag.value = true
        assertEquals(OnboardingGateState.App, viewModel().uiState.value)
    }

    @Test
    fun finishingEntersTheAppButStartIsKeptWhileOnboarding() {
        val vm = viewModel()
        assertEquals(OnboardingGateState.Flow(OnboardingStep.Welcome), vm.uiState.value)

        // Crear cuentas cambia el paso resuelto, pero el flujo no se reinicia a mitad de camino.
        addAccount()
        assertEquals(OnboardingGateState.Flow(OnboardingStep.Welcome), vm.uiState.value)

        onboarding.flag.value = true
        assertEquals(OnboardingGateState.App, vm.uiState.value)
    }
}
