package com.al3xiel.my_finances.core.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class MainScaffoldUiState(
    val stacks: TabBackStacks = TabBackStacks(),
    val registerActions: List<RegisterAction> = emptyList(),
    val moreEntries: List<MoreMenuEntry> = emptyList(),
    val registerSheetVisible: Boolean = false,
) {
    val canGoBack: Boolean get() = stacks.back() != null

    /** The "+" is offered only on the root of the tabs that register, and only if a feature contributed actions. */
    val showRegisterFab: Boolean
        get() = registerActions.isNotEmpty() && stacks.selected.showsRegisterFab && stacks.activeStack.size == 1
}

sealed interface MainScaffoldUiAction {
    data class SelectTab(val tab: MainTab) : MainScaffoldUiAction
    data class Push(val route: Route) : MainScaffoldUiAction
    data object Back : MainScaffoldUiAction
    data object OpenRegisterSheet : MainScaffoldUiAction
    data object DismissRegisterSheet : MainScaffoldUiAction
    data class ChooseRegisterAction(val action: RegisterAction) : MainScaffoldUiAction
}

@HiltViewModel
class MainScaffoldViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    registerActions: RegisterActionRegistry,
    moreMenu: MoreMenuRegistry,
    val routeContributors: Set<@JvmSuppressWildcards RouteContributor>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainScaffoldUiState(
            stacks = restoreStacks(),
            registerActions = registerActions.actions,
            moreEntries = moreMenu.entries,
        ),
    )
    val uiState: StateFlow<MainScaffoldUiState> = _uiState.asStateFlow()

    fun onAction(action: MainScaffoldUiAction) {
        when (action) {
            is MainScaffoldUiAction.SelectTab -> updateStacks { it.select(action.tab) }
            is MainScaffoldUiAction.Push -> updateStacks { it.push(action.route) }
            MainScaffoldUiAction.Back -> updateStacks { it.back() ?: it }
            MainScaffoldUiAction.OpenRegisterSheet -> _uiState.update { it.copy(registerSheetVisible = true) }
            MainScaffoldUiAction.DismissRegisterSheet -> _uiState.update { it.copy(registerSheetVisible = false) }
            is MainScaffoldUiAction.ChooseRegisterAction -> {
                _uiState.update { it.copy(registerSheetVisible = false) }
                updateStacks { it.push(action.action.route) }
            }
        }
    }

    private fun updateStacks(transform: (TabBackStacks) -> TabBackStacks) {
        _uiState.update { it.copy(stacks = transform(it.stacks)) }
        savedStateHandle[STACKS_KEY] = Json.encodeToString(TabBackStacks.serializer(), _uiState.value.stacks)
    }

    private fun restoreStacks(): TabBackStacks {
        val json = savedStateHandle.get<String>(STACKS_KEY) ?: return TabBackStacks()
        return try {
            Json.decodeFromString(TabBackStacks.serializer(), json)
        } catch (_: SerializationException) {
            TabBackStacks()
        } catch (_: IllegalArgumentException) {
            TabBackStacks()
        }
    }

    private companion object {
        const val STACKS_KEY = "tab_stacks"
    }
}
