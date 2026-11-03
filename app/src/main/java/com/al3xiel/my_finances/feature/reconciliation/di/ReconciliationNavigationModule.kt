package com.al3xiel.my_finances.feature.reconciliation.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.core.navigation.DiagnosticExport
import com.al3xiel.my_finances.core.navigation.DictionaryExport
import com.al3xiel.my_finances.core.navigation.ExpenseEditor
import com.al3xiel.my_finances.core.navigation.IncomeEditor
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.ReconciliationDetail
import com.al3xiel.my_finances.core.navigation.ReconciliationResolve
import com.al3xiel.my_finances.core.navigation.ReconciliationStart
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.core.navigation.TransferEditor
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.reconciliation.ui.ReconciliationDetailScreen
import com.al3xiel.my_finances.feature.reconciliation.ui.ReconciliationResolveScreen
import com.al3xiel.my_finances.feature.reconciliation.ui.ReconciliationStartScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object ReconciliationNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<ReconciliationStart> { key ->
                val navigator = LocalNavigator.current
                ReconciliationStartScreen(
                    accountId = AccountId(key.accountId),
                    // S60 no se queda en la pila: si no, Atrás desde S61 volvería a saltar a la conciliación.
                    onOpen = { id ->
                        navigator.back()
                        navigator.push(ReconciliationDetail(id.value))
                    },
                )
            }
            entry<ReconciliationDetail> { key ->
                val navigator = LocalNavigator.current
                val id = key.reconciliationId
                ReconciliationDetailScreen(
                    reconciliationId = ReconciliationId(id),
                    onRegisterForgotten = { type ->
                        when (type) {
                            MovementType.INCOME -> navigator.push(IncomeEditor(reconciliationId = id))
                            MovementType.EXPENSE -> navigator.push(ExpenseEditor(reconciliationId = id))
                            MovementType.TRANSFER -> navigator.push(TransferEditor(reconciliationId = id))
                            else -> Unit
                        }
                    },
                    onCorrect = { movement ->
                        when (movement.type) {
                            MovementType.INCOME -> navigator.push(IncomeEditor(id = movement.id.value, reconciliationId = id))
                            MovementType.EXPENSE -> navigator.push(ExpenseEditor(id = movement.id.value, reconciliationId = id))
                            else -> Unit
                        }
                    },
                    onResolve = { navigator.push(ReconciliationResolve(id)) },
                    onFinished = navigator::back,
                    onExportDiagnostic = { navigator.push(DiagnosticExport(id)) },
                    onExportDictionary = { navigator.push(DictionaryExport(id)) },
                )
            }
            entry<ReconciliationResolve> { key ->
                val navigator = LocalNavigator.current
                ReconciliationResolveScreen(reconciliationId = ReconciliationId(key.reconciliationId), onFinished = navigator::back)
            }
        }
    }
}
