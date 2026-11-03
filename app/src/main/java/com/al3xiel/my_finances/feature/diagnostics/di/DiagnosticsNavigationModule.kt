package com.al3xiel.my_finances.feature.diagnostics.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.core.navigation.DevTools
import com.al3xiel.my_finances.core.navigation.DiagnosticExport
import com.al3xiel.my_finances.core.navigation.DiagnosticWorkspace
import com.al3xiel.my_finances.core.navigation.DictionaryExport
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.diagnostics.ui.DevToolsScreen
import com.al3xiel.my_finances.feature.diagnostics.ui.DiagnosticExportScreen
import com.al3xiel.my_finances.feature.diagnostics.ui.DiagnosticWorkspaceScreen
import com.al3xiel.my_finances.feature.diagnostics.ui.DictionaryExportScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Rutas de S63–S65 y S73. Sin `intent-filter`: el paquete y el diccionario se abren solo con el selector del sistema. */
@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<DevTools> {
                val navigator = LocalNavigator.current
                DevToolsScreen(onOpenPackage = { navigator.push(DiagnosticWorkspace) })
            }
            entry<DiagnosticExport> { key ->
                val navigator = LocalNavigator.current
                DiagnosticExportScreen(reconciliationId = key.reconciliationId?.let { ReconciliationId(it) }, onFinished = navigator::back)
            }
            entry<DiagnosticWorkspace> { DiagnosticWorkspaceScreen() }
            entry<DictionaryExport> { key ->
                val navigator = LocalNavigator.current
                DictionaryExportScreen(reconciliationId = ReconciliationId(key.reconciliationId), onFinished = navigator::back)
            }
        }
    }
}
