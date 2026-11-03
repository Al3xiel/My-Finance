package com.al3xiel.my_finances.feature.privacy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.time.MonotonicClock
import com.al3xiel.my_finances.feature.privacy.domain.repository.DevToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AboutUiState(
    val version: String = "",
    val devToolsEnabled: Boolean = false,
    /** Toques que faltan para activar las herramientas; solo se muestra en los últimos para no anunciar el gesto. */
    val tapsRemaining: Int? = null,
)

sealed interface AboutUiAction {
    data object VersionTapped : AboutUiAction
}

/** "Acerca de": versión y el gesto de 7 toques seguidos que activa las herramientas de desarrollador (S73). */
@HiltViewModel
class AboutViewModel @Inject constructor(
    private val devTools: DevToolsRepository,
    private val clock: MonotonicClock,
    @Named("appVersion") private val version: String,
) : ViewModel() {
    private val taps = MutableStateFlow(0)
    private var lastTapAt = Long.MIN_VALUE

    val uiState: StateFlow<AboutUiState> = combine(devTools.enabled, taps) { enabled, count ->
        val remaining = (REQUIRED_TAPS - count).takeIf { !enabled && count >= REVEAL_AFTER && it > 0 }
        AboutUiState(version, enabled, remaining)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AboutUiState(version))

    fun onAction(action: AboutUiAction) {
        when (action) {
            AboutUiAction.VersionTapped -> tapped()
        }
    }

    private fun tapped() {
        if (uiState.value.devToolsEnabled) return
        val now = clock.nowMillis()
        val count = if (now - lastTapAt > MAX_GAP_MILLIS) 1 else taps.value + 1
        lastTapAt = now
        taps.value = count
        if (count >= REQUIRED_TAPS) {
            taps.update { 0 }
            viewModelScope.launch { devTools.setEnabled(true) }
        }
    }

    private companion object {
        const val REQUIRED_TAPS = 7
        const val REVEAL_AFTER = 4
        const val MAX_GAP_MILLIS = 2_000L
    }
}
