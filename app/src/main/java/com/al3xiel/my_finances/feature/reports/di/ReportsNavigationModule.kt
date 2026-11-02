package com.al3xiel.my_finances.feature.reports.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.MovementsFiltered
import com.al3xiel.my_finances.core.navigation.ReportByAccount
import com.al3xiel.my_finances.core.navigation.ReportByCategory
import com.al3xiel.my_finances.core.navigation.ReportByTag
import com.al3xiel.my_finances.core.navigation.ReportExport
import com.al3xiel.my_finances.core.navigation.Reports
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.feature.reports.ui.ReportByAccountScreen
import com.al3xiel.my_finances.feature.reports.ui.ReportByCategoryScreen
import com.al3xiel.my_finances.feature.reports.ui.ReportByTagScreen
import com.al3xiel.my_finances.feature.reports.ui.ReportExportScreen
import com.al3xiel.my_finances.feature.reports.ui.ReportSummaryScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Conecta Reportes (S40–S43 y S45) con la navegación; la pestaña deja de ser relleno. */
@Module
@InstallIn(SingletonComponent::class)
object ReportsNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<Reports> {
                val navigator = LocalNavigator.current
                ReportSummaryScreen(
                    onOpenByCategory = { navigator.push(ReportByCategory(it.from.toString(), it.to.toString())) },
                    onOpenByTag = { navigator.push(ReportByTag(it.from.toString(), it.to.toString())) },
                    onOpenByAccount = { navigator.push(ReportByAccount(it.from.toString(), it.to.toString())) },
                    onExport = { navigator.push(ReportExport(it.from.toString(), it.to.toString())) },
                )
            }
            entry<ReportByCategory> { key ->
                val navigator = LocalNavigator.current
                ReportByCategoryScreen(
                    from = key.from, to = key.to,
                    onOpenMovements = { navigator.push(MovementsFiltered(from = key.from, to = key.to, categoryId = it)) },
                )
            }
            entry<ReportByTag> { key ->
                val navigator = LocalNavigator.current
                ReportByTagScreen(
                    from = key.from, to = key.to,
                    onOpenMovements = { navigator.push(MovementsFiltered(from = key.from, to = key.to, tagId = it)) },
                )
            }
            entry<ReportByAccount> { key ->
                val navigator = LocalNavigator.current
                ReportByAccountScreen(
                    from = key.from, to = key.to,
                    onOpenMovements = { navigator.push(MovementsFiltered(from = key.from, to = key.to, accountId = it)) },
                )
            }
            entry<ReportExport> { key -> ReportExportScreen(from = key.from, to = key.to) }
        }
    }
}
