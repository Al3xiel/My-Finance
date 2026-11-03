package com.al3xiel.my_finances.feature.diagnostics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.di.IoDispatcher
import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.Authenticator
import com.al3xiel.my_finances.domain.usecase.ReplayProjectionsUseCase
import com.al3xiel.my_finances.domain.usecase.VerifyEventChainUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ObserveNeedsReviewCountUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ReadRawEventsUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.TableFingerprintUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S73: herramientas de desarrollador. Pide biometría o credencial en cada entrada (aunque el bloqueo de la app esté apagado) y
 * no muestra ni hace nada antes. Re-proyectar es `MANUAL`: reconstruye todo y deja `ProjectionsRebuilt`.
 */
@HiltViewModel
class DevToolsViewModel @Inject constructor(
    private val verifyChain: VerifyEventChainUseCase,
    private val replay: ReplayProjectionsUseCase,
    private val fingerprints: TableFingerprintUseCase,
    private val rawEvents: ReadRawEventsUseCase,
    private val needsReview: ObserveNeedsReviewCountUseCase,
    private val authenticator: Authenticator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val state = MutableStateFlow(DevToolsUiState())
    val uiState: StateFlow<DevToolsUiState> = state.asStateFlow()

    private var authenticating = false

    fun onAction(action: DevToolsUiAction) {
        if (action is DevToolsUiAction.Authenticate) return authenticate(action.prompt)
        if (state.value.stage != DevToolsStage.READY && action != DevToolsUiAction.DismissMessage) return
        when (action) {
            is DevToolsUiAction.Authenticate -> Unit
            DevToolsUiAction.VerifyChain -> work { state.update { it.copy(chain = withContext(ioDispatcher) { verifyChain() }) } }
            DevToolsUiAction.Replay -> work { replayManually() }
            DevToolsUiAction.LoadMoreEvents -> work { loadEvents() }
            DevToolsUiAction.DismissMessage -> state.update { it.copy(message = null) }
        }
    }

    private fun authenticate(prompt: AuthPrompt) {
        if (authenticating || state.value.stage != DevToolsStage.LOCKED) return
        if (authenticator.availability() != AuthAvailability.READY) {
            state.update { it.copy(message = DevToolsMessage.NO_CREDENTIAL) }
            return
        }
        authenticating = true
        viewModelScope.launch {
            try {
                when (authenticator.authenticate(prompt)) {
                    AuthResult.Success -> {
                        state.update { it.copy(stage = DevToolsStage.READY, message = null) }
                        loadEvents()
                        state.update { it.copy(needsReviewCount = needsReview().first()) }
                    }
                    AuthResult.Cancelled -> Unit
                    AuthResult.LockedOut, AuthResult.Failed -> state.update { it.copy(message = DevToolsMessage.AUTH_FAILED) }
                }
            } finally {
                authenticating = false
            }
        }
    }

    private fun work(block: suspend () -> Unit) {
        state.update { it.copy(stage = DevToolsStage.WORKING) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                state.update { it.copy(message = DevToolsMessage.UNEXPECTED) }
            } finally {
                state.update { it.copy(stage = DevToolsStage.READY) }
            }
        }
    }

    private suspend fun replayManually() {
        val (before, after) = withContext(ioDispatcher) {
            val before = fingerprints.snapshot()
            replay(REASON_MANUAL)
            before to fingerprints.snapshot()
        }
        state.update { it.copy(changes = fingerprints.compare(before, after), chain = null) }
    }

    private suspend fun loadEvents() {
        val after = state.value.events.lastOrNull()?.sequence ?: 0L
        val page = withContext(ioDispatcher) { rawEvents(after) }
        state.update { it.copy(events = it.events + page.items, hasMoreEvents = page.hasMore) }
    }

    private companion object {
        const val REASON_MANUAL = "MANUAL"
    }
}
