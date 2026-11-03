package com.al3xiel.my_finances.feature.expectedincome.di

import com.al3xiel.my_finances.core.navigation.SettingsSection
import com.al3xiel.my_finances.core.navigation.SettingsSectionContributor
import com.al3xiel.my_finances.feature.expectedincome.ui.ReminderSettingsScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Suma la sección de recordatorios a Ajustes (S72). */
@Module
@InstallIn(SingletonComponent::class)
object ExpectedIncomeSettingsModule {
    @Provides
    @IntoSet
    fun settingsSections(): SettingsSectionContributor = SettingsSectionContributor {
        listOf(SettingsSection(id = "expected_income_reminders", order = 10) { ReminderSettingsScreen() })
    }
}
