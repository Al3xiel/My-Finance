package com.al3xiel.my_finances.feature.funds.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.ExpenseEditor
import com.al3xiel.my_finances.core.navigation.FundCount
import com.al3xiel.my_finances.core.navigation.FundDetail
import com.al3xiel.my_finances.core.navigation.FundEditor
import com.al3xiel.my_finances.core.navigation.Funds
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.MoreMenuContributor
import com.al3xiel.my_finances.core.navigation.MoreMenuEntry
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.feature.funds.ui.FundCountScreen
import com.al3xiel.my_finances.feature.funds.ui.FundDetailScreen
import com.al3xiel.my_finances.feature.funds.ui.FundEditorScreen
import com.al3xiel.my_finances.feature.funds.ui.FundsScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Conecta Fondos (lista, detalle, arqueo y editor) con la navegación; se llega desde "Más". */
@Module
@InstallIn(SingletonComponent::class)
object FundsNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<Funds> {
                val navigator = LocalNavigator.current
                FundsScreen(
                    onOpenFund = { navigator.push(FundDetail(it.value)) },
                    onCreateFund = { navigator.push(FundEditor()) },
                )
            }
            entry<FundDetail> { key ->
                val navigator = LocalNavigator.current
                FundDetailScreen(
                    fundId = FundId(key.id),
                    onCount = { navigator.push(FundCount(it.value)) },
                    onEdit = { navigator.push(FundEditor(it.value)) },
                    onAddExpense = { navigator.push(ExpenseEditor(presetFundId = it.value)) },
                )
            }
            entry<FundCount> { key ->
                val navigator = LocalNavigator.current
                FundCountScreen(fundId = FundId(key.id), onFinished = navigator::back)
            }
            entry<FundEditor> { key ->
                val navigator = LocalNavigator.current
                FundEditorScreen(fundId = key.id, onFinished = navigator::back)
            }
        }
    }

    @Provides
    @IntoSet
    fun moreMenu(): MoreMenuContributor = MoreMenuContributor {
        listOf(MoreMenuEntry("funds", R.string.more_entry_funds, Funds, order = 20))
    }
}
