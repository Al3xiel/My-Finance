package com.al3xiel.my_finances.feature.onboarding.domain.usecase

import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.onboarding.domain.model.OnboardingStep
import com.al3xiel.my_finances.feature.onboarding.domain.repository.OnboardingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Decide por cuentas (activas o archivadas) y bandera, no por eventos (D60). */
class ResolveOnboardingStepUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val onboarding: OnboardingRepository,
) {
    operator fun invoke(): Flow<OnboardingStep> =
        combine(accounts.observeAccounts(), onboarding.completed) { list, completed ->
            when {
                completed -> OnboardingStep.Done
                list.isEmpty() -> OnboardingStep.Welcome
                else -> OnboardingStep.Security
            }
        }.distinctUntilChanged()
}
