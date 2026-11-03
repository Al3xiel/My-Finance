package com.al3xiel.my_finances.feature.movements.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.AppIcons
import com.al3xiel.my_finances.core.navigation.ExpenseEditor
import com.al3xiel.my_finances.core.navigation.IncomeEditor
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.Navigator
import com.al3xiel.my_finances.core.navigation.MovementDetail
import com.al3xiel.my_finances.core.navigation.Movements
import com.al3xiel.my_finances.core.navigation.MovementsFiltered
import com.al3xiel.my_finances.feature.movements.ui.MovementsPreset
import com.al3xiel.my_finances.core.navigation.RegisterAction
import com.al3xiel.my_finances.core.navigation.RegisterActionContributor
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.feature.movements.ui.ExpenseEditorScreen
import com.al3xiel.my_finances.feature.movements.ui.IncomeEditorScreen
import com.al3xiel.my_finances.feature.movements.ui.MovementDetailScreen
import com.al3xiel.my_finances.feature.movements.ui.MovementsScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object MovementsNavigationModule {

    @Provides
    @IntoSet
    fun routes(): RouteContributor = object : RouteContributor {
        override fun EntryProviderScope<Route>.register() {
            entry<Movements> {
                val navigator = LocalNavigator.current
                MovementsScreen(
                    onOpenMovement = { navigator.push(MovementDetail(it.value)) },
                    onCorrect = { id, type -> navigator.correct(id, type) },
                )
            }
            entry<MovementsFiltered> { key ->
                val navigator = LocalNavigator.current
                MovementsScreen(
                    onOpenMovement = { navigator.push(MovementDetail(it.value)) },
                    onCorrect = { id, type -> navigator.correct(id, type) },
                    preset = MovementsPreset(key.from, key.to, key.categoryId, key.tagId, key.accountId),
                )
            }
            entry<MovementDetail> { key ->
                val navigator = LocalNavigator.current
                MovementDetailScreen(
                    movementId = MovementId(key.id),
                    onCorrect = { id, type -> navigator.correct(id, type) },
                )
            }
            entry<ExpenseEditor> { key ->
                val navigator = LocalNavigator.current
                ExpenseEditorScreen(
                    movementId = key.id?.let(::MovementId),
                    onFinished = navigator::back,
                    presetPoolId = key.presetPoolId?.let(::PoolId),
                    presetFundId = key.presetFundId?.let(::FundId),
                    reconciliationId = key.reconciliationId?.let(::ReconciliationId),
                )
            }
            entry<IncomeEditor> { key ->
                val navigator = LocalNavigator.current
                IncomeEditorScreen(
                    movementId = key.id?.let(::MovementId),
                    onFinished = navigator::back,
                    expectedIncomeId = key.expectedIncomeId,
                    reconciliationId = key.reconciliationId?.let(::ReconciliationId),
                )
            }
        }
    }

    @Provides
    @IntoSet
    fun registerActions(): RegisterActionContributor = RegisterActionContributor {
        listOf(
            RegisterAction("expense", R.string.register_action_expense, ExpenseEditor(), order = 10, iconRes = AppIcons.ActionExpense),
            RegisterAction("income", R.string.register_action_income, IncomeEditor(), order = 20),
        )
    }
}

/**
 * Corregir abre el editor del tipo del movimiento. Transferencias (D12), reembolsos, préstamos y pagos (D20), el gasto no
 * registrado de un fondo (D25) y el ajuste (D33) no se corrigen.
 */
private fun Navigator.correct(id: MovementId, type: MovementType) {
    when (type) {
        MovementType.EXPENSE -> push(ExpenseEditor(id.value))
        MovementType.INCOME -> push(IncomeEditor(id.value))
        MovementType.TRANSFER, MovementType.REIMBURSEMENT,
        MovementType.LOAN_DISBURSEMENT, MovementType.LOAN_REPAYMENT, MovementType.FUND_UNTRACKED_SPEND,
        MovementType.ADJUSTMENT -> Unit
    }
}
