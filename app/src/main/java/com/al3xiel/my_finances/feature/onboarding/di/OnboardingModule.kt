package com.al3xiel.my_finances.feature.onboarding.di

import com.al3xiel.my_finances.feature.onboarding.data.OnboardingPreferences
import com.al3xiel.my_finances.feature.onboarding.domain.repository.OnboardingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface OnboardingModule {
    @Binds
    fun onboardingRepository(impl: OnboardingPreferences): OnboardingRepository
}
