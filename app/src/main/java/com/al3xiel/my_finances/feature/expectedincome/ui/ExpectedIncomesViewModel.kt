package com.al3xiel.my_finances.feature.expectedincome.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeRules
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.CancelExpectedIncomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Un esperado con los nombres que la lista necesita; la cuenta archivada se marca porque exige editar antes de confirmar. */
data class ExpectedIncomeItem(
    val income: ExpectedIncome,
    val accountName: String,
    val sourceName: String,
    val accountArchived: Boolean,
) {
    val canConfirm get() = ExpectedIncomeRules.isPending(income.status) && !accountArchived
    val isPending get() = ExpectedIncomeRules.isPending(income.status)
}

data class ExpectedIncomesUiState(
    val loaded: Boolean = false,
    val items: List<ExpectedIncomeItem> = emptyList(),
    /** El esperado que se está por cancelar (pide un motivo opcional). */
    val cancelling: ExpectedIncomeId? = null,
    val error: BusinessError? = null,
)

sealed interface ExpectedIncomesUiAction {
    data class AskCancel(val id: ExpectedIncomeId) : ExpectedIncomesUiAction
    data class ConfirmCancel(val reason: String?) : ExpectedIncomesUiAction
    data object DismissCancel : ExpectedIncomesUiAction
    data object DismissError : ExpectedIncomesUiAction
}

/** S55 (lista): estado efectivo, rango y acciones. Confirmar y editar abren otras pantallas, así que los resuelve la navegación. */
@HiltViewModel
class ExpectedIncomesViewModel @Inject constructor(
    expectedIncomes: ExpectedIncomeRepository,
    accounts: AccountRepository,
    incomeSources: IncomeSourceRepository,
    private val cancelExpectedIncome: CancelExpectedIncomeUseCase,
) : ViewModel() {
    private data class Form(val cancelling: ExpectedIncomeId? = null, val error: BusinessError? = null)

    private val form = MutableStateFlow(Form())
    private var submitting = false

    val uiState: StateFlow<ExpectedIncomesUiState> = combine(
        expectedIncomes.observeAll(), accounts.observeAccounts(), incomeSources.observeAll(), form,
    ) { list, accountList, sourceList, form ->
        val accountById = accountList.associate { it.account.id to it.account }
        val sourceById = sourceList.associateBy { it.id }
        ExpectedIncomesUiState(
            loaded = true,
            items = list
                .map { income ->
                    ExpectedIncomeItem(
                        income = income,
                        accountName = accountById[income.accountId]?.name.orEmpty(),
                        sourceName = sourceById[income.incomeSourceId]?.name.orEmpty(),
                        accountArchived = accountById[income.accountId]?.isArchived == true,
                    )
                }
                .sortedWith(compareBy({ it.income.status.listOrder() }, { it.income.range.from }, { it.income.id.value })),
            cancelling = form.cancelling,
            error = form.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ExpectedIncomesUiState())

    fun onAction(action: ExpectedIncomesUiAction) {
        when (action) {
            is ExpectedIncomesUiAction.AskCancel -> form.update { it.copy(cancelling = action.id, error = null) }
            ExpectedIncomesUiAction.DismissCancel -> form.update { it.copy(cancelling = null) }
            ExpectedIncomesUiAction.DismissError -> form.update { it.copy(error = null) }
            is ExpectedIncomesUiAction.ConfirmCancel -> cancel(action.reason)
        }
    }

    private fun cancel(reason: String?) {
        val id = form.value.cancelling ?: return
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            val error = attempt { cancelExpectedIncome(id, reason) }.errorOrNull()
            submitting = false
            form.update { it.copy(cancelling = null, error = error) }
        }
    }
}

/** Primero lo que pide atención: vencidos, luego programados, y al final lo ya resuelto. */
internal fun ExpectedIncomeStatus.listOrder() = when (this) {
    ExpectedIncomeStatus.OVERDUE -> 0
    ExpectedIncomeStatus.SCHEDULED -> 1
    ExpectedIncomeStatus.CONFIRMED -> 2
    ExpectedIncomeStatus.CANCELLED -> 3
}
