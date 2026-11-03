package com.al3xiel.my_finances.feature.expectedincome.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.ExpectedIncomeEditor
import com.al3xiel.my_finances.core.navigation.ExpectedIncomes
import com.al3xiel.my_finances.core.navigation.IncomeEditor
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.MoreMenuContributor
import com.al3xiel.my_finances.core.navigation.MoreMenuEntry
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.feature.expectedincome.ui.ExpectedIncomeEditorScreen
import com.al3xiel.my_finances.feature.expectedincome.ui.ExpectedIncomesScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Conecta Ingresos esperados (lista y editor) con la navegación; se llega desde "Más". Confirmar abre S23 prellenado. */
@Module
@InstallIn(SingletonComponent::class)
object ExpectedIncomeNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<ExpectedIncomes> {
                val navigator = LocalNavigator.current
                ExpectedIncomesScreen(
                    onSchedule = { navigator.push(ExpectedIncomeEditor()) },
                    onEdit = { navigator.push(ExpectedIncomeEditor(it.value)) },
                    onConfirm = { navigator.push(IncomeEditor(expectedIncomeId = it.value)) },
                )
            }
            entry<ExpectedIncomeEditor> { key ->
                val navigator = LocalNavigator.current
                ExpectedIncomeEditorScreen(expectedIncomeId = key.id, onFinished = navigator::back)
            }
        }
    }

    @Provides
    @IntoSet
    fun moreMenu(): MoreMenuContributor = MoreMenuContributor {
        listOf(MoreMenuEntry("expected_incomes", R.string.more_entry_expected_incomes, ExpectedIncomes, order = 30))
    }
}
