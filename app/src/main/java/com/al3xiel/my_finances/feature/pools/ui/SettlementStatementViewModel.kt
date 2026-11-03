package com.al3xiel.my_finances.feature.pools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.share.SharedFilesCleaner
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.feature.pools.domain.model.GeneratedStatement
import com.al3xiel.my_finances.feature.pools.domain.model.SettlementStatementContent
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.pools.domain.usecase.GenerateSettlementStatementUseCase
import com.al3xiel.my_finances.feature.pools.domain.usecase.PrepareSettlementStatementUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pedido de compartir; la pantalla lo dibuja (imagen o PDF) y abre el selector de apps. No emite eventos. */
data class ShareRequest(
    val statementId: StatementId,
    val content: SettlementStatementContent,
    val format: StatementFormat,
)

data class SettlementStatementUiState(
    val loaded: Boolean = false,
    val content: SettlementStatementContent? = null,
    val kind: PoolKind? = null,
    val format: StatementFormat = StatementFormat.PDF,
    /** "Esto cierra el periodo" (D17): se muestra antes de emitir el evento. */
    val confirming: Boolean = false,
    /** Ya confirmada: de aquí en adelante solo se vuelve a compartir. */
    val generated: GeneratedStatement? = null,
    val shareRequest: ShareRequest? = null,
    val error: BusinessError? = null,
)

sealed interface SettlementStatementUiAction {
    data class SelectFormat(val format: StatementFormat) : SettlementStatementUiAction
    data object AskConfirm : SettlementStatementUiAction
    data object DismissConfirm : SettlementStatementUiAction
    data object Confirm : SettlementStatementUiAction
    data object ShareAgain : SettlementStatementUiAction
    data object ShareHandled : SettlementStatementUiAction

    /** La pantalla volvió a primer plano tras el selector de apps: se borran los archivos temporales. */
    data object ShareClosed : SettlementStatementUiAction
}

/** S33 (D17): vista previa, formato, confirmación y luego compartir; una rendición generada se comparte de nuevo sin evento. */
@HiltViewModel(assistedFactory = SettlementStatementViewModel.Factory::class)
class SettlementStatementViewModel @AssistedInject constructor(
    @Assisted poolIdValue: String,
    private val pools: PoolRepository,
    private val prepare: PrepareSettlementStatementUseCase,
    private val generate: GenerateSettlementStatementUseCase,
    private val cleaner: SharedFilesCleaner,
    private val sharer: StatementSharer,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(poolIdValue: String): SettlementStatementViewModel
    }

    private val poolId = PoolId(poolIdValue)
    private val state = MutableStateFlow(SettlementStatementUiState())
    private var submitting = false

    val uiState: StateFlow<SettlementStatementUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val kind = pools.find(poolId)?.kind
            when (val result = attempt { prepare(poolId) }) {
                is Outcome.Ok -> state.update { it.copy(loaded = true, kind = kind, content = result.value) }
                is Outcome.Failed -> state.update { it.copy(loaded = true, kind = kind, error = result.error) }
            }
        }
    }

    /** El Intent para compartir ese pedido, o `null` si no se pudo escribir el archivo. No emite eventos. */
    suspend fun shareIntent(request: ShareRequest) = sharer.shareIntent(request)

    fun onAction(action: SettlementStatementUiAction) {
        when (action) {
            is SettlementStatementUiAction.SelectFormat -> state.update { it.copy(format = action.format) }
            SettlementStatementUiAction.AskConfirm ->
                state.update { if (it.content != null && it.generated == null) it.copy(confirming = true) else it }
            SettlementStatementUiAction.DismissConfirm -> state.update { it.copy(confirming = false) }
            SettlementStatementUiAction.Confirm -> confirm()
            SettlementStatementUiAction.ShareAgain -> state.update { current ->
                val generated = current.generated ?: return@update current
                current.copy(shareRequest = ShareRequest(generated.statementId, generated.content, current.format))
            }
            SettlementStatementUiAction.ShareHandled -> state.update { it.copy(shareRequest = null) }
            // NonCancellable: si el usuario sale de la pantalla justo al volver del selector, el borrado termina igual.
            SettlementStatementUiAction.ShareClosed -> viewModelScope.launch { withContext(NonCancellable) { cleaner.clear() } }
        }
    }

    private fun confirm() {
        val current = state.value
        if (submitting || current.generated != null || current.content == null) return
        submitting = true
        state.update { it.copy(confirming = false) }
        viewModelScope.launch {
            when (val result = attempt { generate(poolId, current.format) }) {
                is Outcome.Ok -> state.update {
                    it.copy(
                        generated = result.value,
                        content = result.value.content,
                        shareRequest = ShareRequest(result.value.statementId, result.value.content, current.format),
                    )
                }
                is Outcome.Failed -> {
                    submitting = false
                    state.update { it.copy(error = result.error) }
                }
            }
        }
    }
}
