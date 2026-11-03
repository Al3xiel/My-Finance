package com.al3xiel.my_finances.feature.onboarding.domain.repository

import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    /** `OnboardingCompleted`: se pone solo al terminar el último paso. */
    val completed: Flow<Boolean>

    suspend fun markCompleted()
}
