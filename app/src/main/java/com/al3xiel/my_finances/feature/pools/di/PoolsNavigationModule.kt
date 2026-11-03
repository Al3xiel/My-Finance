package com.al3xiel.my_finances.feature.pools.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.ExpenseEditor
import com.al3xiel.my_finances.core.navigation.LoanDetail
import com.al3xiel.my_finances.core.navigation.LoanEditor
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.PersonDetail
import com.al3xiel.my_finances.core.navigation.PoolDetail
import com.al3xiel.my_finances.core.navigation.PoolEditor
import com.al3xiel.my_finances.core.navigation.Receivables
import com.al3xiel.my_finances.core.navigation.ReimbursementEditor
import com.al3xiel.my_finances.core.navigation.RegisterAction
import com.al3xiel.my_finances.core.navigation.RegisterActionContributor
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.core.navigation.SettlementStatement
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.feature.pools.ui.PoolDetailScreen
import com.al3xiel.my_finances.feature.pools.ui.PoolEditorScreen
import com.al3xiel.my_finances.feature.pools.ui.ReceivablesScreen
import com.al3xiel.my_finances.feature.pools.ui.ReimbursementEditorScreen
import com.al3xiel.my_finances.feature.pools.ui.SettlementStatementScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Conecta Por cobrar, cúmulos, rendiciones, reembolsos, personas y deudas con la navegación. */
@Module
@InstallIn(SingletonComponent::class)
object PoolsNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<Receivables> {
                val navigator = LocalNavigator.current
                ReceivablesScreen(
                    onOpenPerson = { navigator.push(PersonDetail(it.value)) },
                    onOpenPool = { navigator.push(PoolDetail(it.value)) },
                    onOpenLoan = { navigator.push(LoanDetail(it.value)) },
                    onNewPool = { navigator.push(PoolEditor()) },
                    onNewLoan = { navigator.push(LoanEditor()) },
                )
            }
            entry<PoolDetail> { key ->
                val navigator = LocalNavigator.current
                PoolDetailScreen(
                    poolId = PoolId(key.id),
                    onAddExpense = { navigator.push(ExpenseEditor(presetPoolId = it.value)) },
                    onSettle = { navigator.push(SettlementStatement(it.value)) },
                    onReceiveReimbursement = { person, pool -> navigator.push(ReimbursementEditor(person.value, pool.value)) },
                    onEdit = { navigator.push(PoolEditor(it.value)) },
                )
            }
            entry<PoolEditor> { key ->
                val navigator = LocalNavigator.current
                PoolEditorScreen(poolId = key.id, onFinished = navigator::back)
            }
            entry<SettlementStatement> { key -> SettlementStatementScreen(poolId = PoolId(key.poolId)) }
            entry<ReimbursementEditor> { key ->
                val navigator = LocalNavigator.current
                ReimbursementEditorScreen(
                    personId = key.personId?.let(::PersonId),
                    poolId = key.poolId?.let(::PoolId),
                    onFinished = navigator::back,
                )
            }
        }
    }

    /** "Reembolso recibido" en el "+": la persona se elige en la pantalla. */
    @Provides
    @IntoSet
    fun registerActions(): RegisterActionContributor = RegisterActionContributor {
        listOf(RegisterAction("reimbursement", R.string.register_action_reimbursement, ReimbursementEditor(), order = 40))
    }
}
