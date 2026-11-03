package com.al3xiel.my_finances.feature.diagnostics.ui

import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.domain.usecase.ChainVerification
import com.al3xiel.my_finances.feature.diagnostics.domain.model.FingerprintChange
import com.al3xiel.my_finances.feature.diagnostics.domain.model.RawEventLine

/** `LOCKED` = todavía no se confirmó la credencial: S73 no muestra ni hace nada (se pide en cada entrada). */
enum class DevToolsStage { LOCKED, READY, WORKING }

enum class DevToolsMessage { AUTH_FAILED, NO_CREDENTIAL, UNEXPECTED }

data class DevToolsUiState(
    val stage: DevToolsStage = DevToolsStage.LOCKED,
    val chain: ChainVerification? = null,
    /** Tablas antes y después de la última re-proyección; `null` si aún no se hizo una. */
    val changes: List<FingerprintChange>? = null,
    val events: List<RawEventLine> = emptyList(),
    val hasMoreEvents: Boolean = false,
    val needsReviewCount: Int = 0,
    val message: DevToolsMessage? = null,
)

sealed interface DevToolsUiAction {
    data class Authenticate(val prompt: AuthPrompt) : DevToolsUiAction
    data object VerifyChain : DevToolsUiAction
    data object Replay : DevToolsUiAction
    data object LoadMoreEvents : DevToolsUiAction
    data object DismissMessage : DevToolsUiAction
}
