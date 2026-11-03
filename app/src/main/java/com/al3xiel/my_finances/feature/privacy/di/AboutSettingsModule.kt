package com.al3xiel.my_finances.feature.privacy.di

import com.al3xiel.my_finances.core.navigation.SettingsSection
import com.al3xiel.my_finances.core.navigation.SettingsSectionContributor
import com.al3xiel.my_finances.feature.privacy.ui.AboutSection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Suma "Acerca de" al final de Ajustes (S72). */
@Module
@InstallIn(SingletonComponent::class)
object AboutSettingsModule {
    @Provides
    @IntoSet
    fun settingsSections(): SettingsSectionContributor = SettingsSectionContributor {
        listOf(SettingsSection(id = "about", order = 90) { AboutSection() })
    }
}
