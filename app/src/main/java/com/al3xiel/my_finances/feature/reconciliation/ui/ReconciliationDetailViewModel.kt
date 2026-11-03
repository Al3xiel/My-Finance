package com.al3xiel.my_finances.feature.reconciliation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationDetail
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.CancelReconciliationUseCase
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.LinkMovementUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReconciliationDetailUiState(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val detail: ReconciliationDetail? = null,
    val accountName: String? = null,
    /** Solo una conciliación abierta se vincula, se resuelve o se cancela; las demás son de consulta. */
    val canAct: Boolean = false,
    val askingCancel: Boolean = false,
    val error: BusinessError? = null,
    val closed: Boolean = false,
)

sealed interface ReconciliationDetailUiAction {
    data class Link(val movementId: MovementId) : ReconciliationDetailUiAction
    data object AskCancel : ReconciliationDetailUiAction
    data object DismissCancel : ReconciliationDetailUiAction
    data class ConfirmCancel(val reason: String) : ReconciliationDetailUiAction
    data object DismissError : ReconciliationDetailUiAction
}

/** S61: la diferencia restante se recalcula en vivo mientras vinculas lo que explicó la diferencia (C2). */
@HiltViewModel(assistedFactory = ReconciliationDetailViewModel.Factory::class)
class ReconciliationDetailViewModel @AssistedInject constructor(
    @Assisted reconciliationIdValue: String,
    reconciliations: ReconciliationRepository,
    accounts: AccountRepository,
    private val linkMovement: LinkMovementUseCase,
    private val cancelReconciliation: CancelReconciliationUseCase,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(reconciliationIdValue: String): ReconciliationDetailViewModel
    }

    private val reconciliationId = ReconciliationId(reconciliationIdValue)

    private data class Form(val askingCancel: Boolean = false, val error: BusinessError? = null, val closed: Boolean = false)

    private val form = MutableStateFlow(Form())

    val uiState: StateFlow<ReconciliationDetailUiState> = combine(
        reconciliations.observeDetail(reconciliationId).map { detail ->
            detail to detail?.let { accounts.find(it.reconciliation.accountId)?.name }
        },
        form,
    ) { (detail, accountName), form ->
        ReconciliationDetailUiState(
            loaded = true,
            notFound = detail == null,
            detail = detail,
            accountName = accountName,
            canAct = detail?.reconciliation?.status == ReconciliationStatus.OPEN,
            askingCancel = form.askingCancel,
            error = form.error,
            closed = form.closed,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReconciliationDetailUiState())

    fun onAction(action: ReconciliationDetailUiAction) {
        when (action) {
            is ReconciliationDetailUiAction.Link -> link(action.movementId)
            ReconciliationDetailUiAction.AskCancel -> form.update { it.copy(askingCancel = true, error = null) }
            ReconciliationDetailUiAction.DismissCancel -> form.update { it.copy(askingCancel = false, error = null) }
            is ReconciliationDetailUiAction.ConfirmCancel -> cancel(action.reason)
            ReconciliationDetailUiAction.DismissError -> form.update { it.copy(error = null) }
        }
    }

    private fun link(movementId: MovementId) {
        viewModelScope.launch {
            val error = attempt { linkMovement(reconciliationId, movementId) }.errorOrNull()
            form.update { it.copy(error = error) }
        }
    }

    private fun cancel(reason: String) {
        viewModelScope.launch {
            when (val result = attempt { cancelReconciliation(reconciliationId, reason) }) {
                is Outcome.Ok -> form.update { it.copy(askingCancel = false, closed = true, error = null) }
                is Outcome.Failed -> form.update { it.copy(error = result.error) }
            }
        }
    }
}
