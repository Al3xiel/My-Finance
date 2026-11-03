package com.al3xiel.my_finances.feature.onboarding.domain

import com.al3xiel.my_finances.feature.onboarding.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeOnboardingRepository(initial: Boolean = false) : OnboardingRepository {
    val flag = MutableStateFlow(initial)
    override val completed = flag
    override suspend fun markCompleted() {
        flag.value = true
    }
}
