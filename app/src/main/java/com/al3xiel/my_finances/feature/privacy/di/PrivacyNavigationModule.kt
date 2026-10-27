package com.al3xiel.my_finances.feature.privacy.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.MoreMenuContributor
import com.al3xiel.my_finances.core.navigation.MoreMenuEntry
import com.al3xiel.my_finances.core.navigation.Privacy
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.core.navigation.Settings
import com.al3xiel.my_finances.feature.privacy.ui.PrivacyScreen
import com.al3xiel.my_finances.feature.privacy.ui.SettingsScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object PrivacyNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<Privacy> { PrivacyScreen() }
            entry<Settings> { SettingsScreen() }
        }
    }

    @Provides
    @IntoSet
    fun moreMenu(): MoreMenuContributor = MoreMenuContributor {
        listOf(
            MoreMenuEntry("privacy", R.string.more_entry_privacy, Privacy, order = 80),
            MoreMenuEntry("settings", R.string.more_entry_settings, Settings, order = 90),
        )
    }
}
