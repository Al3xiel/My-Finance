package com.al3xiel.my_finances.feature.onboarding.domain

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.feature.accounts.domain.FakeAccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.onboarding.domain.model.OnboardingStep
import com.al3xiel.my_finances.feature.onboarding.domain.usecase.ResolveOnboardingStepUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveOnboardingStepUseCaseTest {
    private val accounts = FakeAccountRepository()
    private val onboarding = FakeOnboardingRepository()
    private val resolve = ResolveOnboardingStepUseCase(accounts, onboarding)

    private fun addAccount(archived: Boolean = false) {
        val id = AccountId("a1")
        accounts.accounts[id] = Account(id, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, archived)
    }

    @Test
    fun noAccountsNoFlagIsWelcome() = runTest { assertEquals(OnboardingStep.Welcome, resolve().first()) }

    @Test
    fun accountsWithoutFlagResumeAtSecurity() = runTest {
        addAccount()
        assertEquals(OnboardingStep.Security, resolve().first())
    }

    @Test
    fun archivedAccountsStillCount() = runTest {
        addAccount(archived = true)
        assertEquals(OnboardingStep.Security, resolve().first())
    }

    @Test
    fun flagIsDoneEvenWithoutAccounts() = runTest {
        onboarding.markCompleted()
        assertEquals(OnboardingStep.Done, resolve().first())
    }

    @Test
    fun wipedDataGoesBackToWelcome() = runTest {
        addAccount()
        onboarding.markCompleted()
        assertEquals(OnboardingStep.Done, resolve().first())
        accounts.accounts.clear()
        onboarding.flag.value = false
        assertEquals(OnboardingStep.Welcome, resolve().first())
    }
}
