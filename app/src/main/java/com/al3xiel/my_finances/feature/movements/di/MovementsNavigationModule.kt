package com.al3xiel.my_finances.feature.movements.di

import androidx.navigation3.runtime.EntryProviderScope
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.ExpenseEditor
import com.al3xiel.my_finances.core.navigation.IncomeEditor
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.navigation.MovementDetail
import com.al3xiel.my_finances.core.navigation.Movements
import com.al3xiel.my_finances.core.navigation.RegisterAction
import com.al3xiel.my_finances.core.navigation.RegisterActionContributor
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.RouteContributor
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PoolId
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
                MovementsScreen(onOpenMovement = { navigator.push(MovementDetail(it.value)) })
            }
            entry<MovementDetail> { key ->
                val navigator = LocalNavigator.current
                MovementDetailScreen(
                    movementId = MovementId(key.id),
                    // Corregir abre el editor del tipo del movimiento; las transferencias no llegan aquí (D12).
                    onCorrect = { id, type ->
                        when (type) {
                            MovementType.EXPENSE -> navigator.push(ExpenseEditor(id.value))
                            MovementType.INCOME -> navigator.push(IncomeEditor(id.value))
                            // Transferencias (D12), reembolsos, préstamos y pagos (D20) no se corrigen.
                            MovementType.TRANSFER, MovementType.REIMBURSEMENT,
                            MovementType.LOAN_DISBURSEMENT, MovementType.LOAN_REPAYMENT -> Unit
                        }
                    },
                )
            }
            entry<ExpenseEditor> { key ->
                val navigator = LocalNavigator.current
                ExpenseEditorScreen(
                    movementId = key.id?.let(::MovementId),
                    onFinished = navigator::back,
                    presetPoolId = key.presetPoolId?.let(::PoolId),
                )
            }
            entry<IncomeEditor> { key ->
                val navigator = LocalNavigator.current
                IncomeEditorScreen(movementId = key.id?.let(::MovementId), onFinished = navigator::back)
            }
        }
    }

    @Provides
    @IntoSet
    fun registerActions(): RegisterActionContributor = RegisterActionContributor {
        listOf(
            RegisterAction("expense", R.string.register_action_expense, ExpenseEditor(), order = 10),
            RegisterAction("income", R.string.register_action_income, IncomeEditor(), order = 20),
        )
    }
}
