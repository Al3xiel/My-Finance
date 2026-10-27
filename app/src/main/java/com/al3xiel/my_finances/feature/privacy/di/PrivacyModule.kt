package com.al3xiel.my_finances.feature.privacy.di

import com.al3xiel.my_finances.feature.privacy.data.CacheWipeStep
import com.al3xiel.my_finances.feature.privacy.data.DataStoreSecuritySettingsRepository
import com.al3xiel.my_finances.feature.privacy.data.DataStoreThemeRepository
import com.al3xiel.my_finances.feature.privacy.data.DatabaseKeyWipeStep
import com.al3xiel.my_finances.feature.privacy.data.DatabasesWipeStep
import com.al3xiel.my_finances.feature.privacy.data.PreferencesWipeStep
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import com.al3xiel.my_finances.feature.privacy.domain.repository.ThemeRepository
import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface PrivacyModule {
    @Binds
    fun securitySettingsRepository(impl: DataStoreSecuritySettingsRepository): SecuritySettingsRepository

    @Binds
    fun themeRepository(impl: DataStoreThemeRepository): ThemeRepository

    @Binds
    @IntoSet
    fun databaseKeyWipeStep(impl: DatabaseKeyWipeStep): WipeStep

    @Binds
    @IntoSet
    fun databasesWipeStep(impl: DatabasesWipeStep): WipeStep

    @Binds
    @IntoSet
    fun preferencesWipeStep(impl: PreferencesWipeStep): WipeStep

    @Binds
    @IntoSet
    fun cacheWipeStep(impl: CacheWipeStep): WipeStep
}
