package com.al3xiel.my_finances.feature.home.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.core.navigation.AccountDetail
import com.al3xiel.my_finances.core.navigation.Backup
import com.al3xiel.my_finances.core.navigation.FundDetail
import com.al3xiel.my_finances.core.navigation.Home
import com.al3xiel.my_finances.core.navigation.IncomeEditor
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.ReconciliationDetail
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.feature.home.domain.model.HomeAlert
import com.al3xiel.my_finances.feature.home.ui.HomeScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** A dónde lleva cada alerta de Inicio (docs/05 S10). */
internal fun HomeAlert.destination(): Route = when (this) {
    is HomeAlert.NeedsReview -> ReconciliationDetail(reconciliationId.value)
    is HomeAlert.OpenReconciliation -> ReconciliationDetail(reconciliationId.value)
    is HomeAlert.OverdueIncome -> IncomeEditor(expectedIncomeId = expectedIncomeId.value)
    HomeAlert.BackupOverdue -> Backup
}

@Module
@InstallIn(SingletonComponent::class)
object HomeNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<Home> {
                val navigator = LocalNavigator.current
                HomeScreen(
                    onOpenAccount = { navigator.push(AccountDetail(it.value)) },
                    onOpenFund = { navigator.push(FundDetail(it.value)) },
                    onOpenAlert = { navigator.push(it.destination()) },
                    onOpenNextIncome = { navigator.push(IncomeEditor(expectedIncomeId = it.value)) },
                )
            }
        }
    }
}
