package com.al3xiel.my_finances.feature.reconciliation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.AdjustmentSpec
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationRules
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.ResolveReconciliationUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_NOTE_LENGTH = 500

data class ReconciliationResolveUiState(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val difference: Money? = null,
    /** Solo las causas que admite la diferencia de ahora (C4). */
    val causes: List<ReconciliationCause> = emptyList(),
    val selectedCause: ReconciliationCause? = null,
    /** El ingreso o egreso no identificado que se crearía; `null` con diferencia 0. */
    val adjustment: AdjustmentSpec? = null,
    val note: String = "",
    val error: BusinessError? = null,
    val resolved: Boolean = false,
)

sealed interface ReconciliationResolveUiAction {
    data class SelectCause(val cause: ReconciliationCause) : ReconciliationResolveUiAction
    data class EditNote(val text: String) : ReconciliationResolveUiAction
    data object Confirm : ReconciliationResolveUiAction
}

/** S62: elegir la causa y ver el ajuste antes de confirmar; el ajuste solo se crea al resolver (C3). */
@HiltViewModel(assistedFactory = ReconciliationResolveViewModel.Factory::class)
class ReconciliationResolveViewModel @AssistedInject constructor(
    @Assisted reconciliationIdValue: String,
    private val reconciliations: ReconciliationRepository,
    private val resolveReconciliation: ResolveReconciliationUseCase,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(reconciliationIdValue: String): ReconciliationResolveViewModel
    }

    private val reconciliationId = ReconciliationId(reconciliationIdValue)
    private val state = MutableStateFlow(ReconciliationResolveUiState())
    private var submitting = false

    val uiState: StateFlow<ReconciliationResolveUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val open = reconciliations.find(reconciliationId)?.takeIf { it.status == ReconciliationStatus.OPEN }
            val difference = open?.let { reconciliations.remainingDifference(reconciliationId) }
            if (difference == null) {
                state.update { it.copy(loaded = true, notFound = true) }
                return@launch
            }
            val causes = ReconciliationRules.allowedCauses(difference).sortedBy { it.ordinal }
            state.update {
                it.copy(
                    loaded = true,
                    difference = difference,
                    causes = causes,
                    // Desconocida es la salida honesta cuando no se halló el origen; no se culpa a la app por defecto.
                    selectedCause = ReconciliationCause.UNKNOWN.takeIf { c -> c in causes } ?: causes.firstOrNull(),
                    adjustment = ReconciliationRules.adjustmentFor(difference),
                )
            }
        }
    }

    fun onAction(action: ReconciliationResolveUiAction) {
        when (action) {
            is ReconciliationResolveUiAction.SelectCause ->
                state.update { if (action.cause in it.causes) it.copy(selectedCause = action.cause, error = null) else it }
            is ReconciliationResolveUiAction.EditNote -> state.update { it.copy(note = action.text.take(MAX_NOTE_LENGTH), error = null) }
            ReconciliationResolveUiAction.Confirm -> confirm()
        }
    }

    private fun confirm() {
        val current = state.value
        val cause = current.selectedCause ?: return
        if (submitting || current.resolved) return
        submitting = true
        viewModelScope.launch {
            val result = attempt { resolveReconciliation(reconciliationId, cause, current.note) }
            submitting = false
            state.update {
                when (result) {
                    is Outcome.Ok -> it.copy(resolved = true)
                    is Outcome.Failed -> it.copy(error = result.error)
                }
            }
        }
    }
}
