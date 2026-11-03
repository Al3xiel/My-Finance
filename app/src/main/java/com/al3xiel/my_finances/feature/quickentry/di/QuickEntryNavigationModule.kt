package com.al3xiel.my_finances.feature.quickentry.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.QuickEntry
import com.al3xiel.my_finances.core.navigation.RegisterAction
import com.al3xiel.my_finances.core.navigation.RegisterActionContributor
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.feature.quickentry.ui.QuickEntryScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Conecta el registro rápido (S25) con la navegación y con el "+" del shell. */
@Module
@InstallIn(SingletonComponent::class)
object QuickEntryNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<QuickEntry> {
                val navigator = LocalNavigator.current
                QuickEntryScreen(onFinished = navigator::back)
            }
        }
    }

    /** "Registro rápido" va al final del "+": los editores completos siguen primero. */
    @Provides
    @IntoSet
    fun registerActions(): RegisterActionContributor = RegisterActionContributor {
        listOf(RegisterAction("quick_entry", R.string.register_action_quick_entry, QuickEntry, order = 90))
    }
}
