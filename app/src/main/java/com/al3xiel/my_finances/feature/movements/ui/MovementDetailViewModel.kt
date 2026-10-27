package com.al3xiel.my_finances.feature.movements.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDetail
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nombres para pintar el detalle, por id; incluye los archivados porque el movimiento puede usarlos. */
data class CatalogNames(
    val accounts: Map<String, String> = emptyMap(),
    val categories: Map<String, String> = emptyMap(),
    val tags: Map<String, String> = emptyMap(),
    val incomeSources: Map<String, String> = emptyMap(),
)

data class MovementDetailUiState(
    val detail: MovementDetail? = null,
    val loaded: Boolean = false,
    val names: CatalogNames = CatalogNames(),
    val showVoidDialog: Boolean = false,
    val negativeWarning: BusinessError.WouldGoNegative? = null,
    val error: BusinessError? = null,
) {
    /** Corregir y anular solo existen para ingresos y gastos que siguen activos (D12). */
    val canEdit: Boolean
        get() = detail?.movement?.let { it.type != MovementType.TRANSFER && it.status == MovementStatus.ACTIVE } == true
}

sealed interface MovementDetailUiAction {
    data object ShowVoidDialog : MovementDetailUiAction
    data object DismissVoidDialog : MovementDetailUiAction
    data class ConfirmVoid(val reason: String) : MovementDetailUiAction
    data object ConfirmNegativeBalance : MovementDetailUiAction
    data object DismissWarning : MovementDetailUiAction
    data object DismissError : MovementDetailUiAction
}

@HiltViewModel(assistedFactory = MovementDetailViewModel.Factory::class)
class MovementDetailViewModel @AssistedInject constructor(
    @Assisted movementIdValue: String,
    movements: MovementRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    tags: TagRepository,
    incomeSources: IncomeSourceRepository,
    private val voidMovement: VoidMovementUseCase,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(movementIdValue: String): MovementDetailViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    private val movementId = MovementId(movementIdValue)

    private data class Local(
        val showVoidDialog: Boolean = false,
        val pendingReason: String? = null,
        val negativeWarning: BusinessError.WouldGoNegative? = null,
        val submitting: Boolean = false,
        val error: BusinessError? = null,
    )

    private val local = MutableStateFlow(Local())

    private val names = combine(
        accounts.observeAccounts(),
        categories.observeAll(),
        tags.observeAll(),
        incomeSources.observeAll(),
    ) { accountList, categoryList, tagList, sourceList ->
        CatalogNames(
            accounts = accountList.associate { it.account.id.value to it.account.name },
            categories = categoryList.associate { it.id.value to it.name },
            tags = tagList.associate { it.id.value to it.name },
            incomeSources = sourceList.associate { it.id.value to it.name },
        )
    }

    val uiState: StateFlow<MovementDetailUiState> = combine(movements.observeDetail(movementId), names, local) { detail, names, local ->
        MovementDetailUiState(
            detail = detail,
            loaded = true,
            names = names,
            showVoidDialog = local.showVoidDialog,
            negativeWarning = local.negativeWarning,
            error = local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MovementDetailUiState())

    fun onAction(action: MovementDetailUiAction) {
        when (action) {
            MovementDetailUiAction.ShowVoidDialog -> local.update { it.copy(showVoidDialog = true) }
            MovementDetailUiAction.DismissVoidDialog -> local.update { it.copy(showVoidDialog = false) }
            is MovementDetailUiAction.ConfirmVoid -> submit(action.reason, confirmNegative = false)
            MovementDetailUiAction.ConfirmNegativeBalance -> {
                if (local.value.submitting) return
                val reason = local.value.pendingReason ?: return
                local.update { it.copy(negativeWarning = null) }
                submit(reason, confirmNegative = true)
            }
            MovementDetailUiAction.DismissWarning -> local.update { it.copy(negativeWarning = null, pendingReason = null) }
            MovementDetailUiAction.DismissError -> local.update { it.copy(error = null) }
        }
    }

    /** Un segundo toque mientras se guarda no emite otro `MovementVoided`. */
    private fun submit(reason: String, confirmNegative: Boolean) {
        if (local.value.submitting) return
        local.update { it.copy(submitting = true, showVoidDialog = false) }
        viewModelScope.launch {
            val error = attempt { voidMovement(movementId, reason, confirmNegative) }.errorOrNull()
            local.update {
                when (error) {
                    is BusinessError.WouldGoNegative -> it.copy(submitting = false, pendingReason = reason, negativeWarning = error)
                    else -> it.copy(submitting = false, pendingReason = null, error = error)
                }
            }
        }
    }
}
