package com.al3xiel.my_finances.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

@Composable
fun MainScaffold(viewModel: MainScaffoldViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MainScaffoldContent(
        state = state,
        onAction = viewModel::onAction,
        entryProvider = rememberRouteEntryProvider(viewModel.routeContributors),
    )
}

@Composable
fun rememberRouteEntryProvider(contributors: Set<RouteContributor>): (Route) -> NavEntry<Route> =
    remember(contributors) {
        entryProvider(fallback = { route -> NavEntry(route) { TabPlaceholderScreen() } }) {
            contributors.forEach { contributor -> with(contributor) { register() } }
        }
    }

@Composable
fun MainScaffoldContent(
    state: MainScaffoldUiState,
    onAction: (MainScaffoldUiAction) -> Unit,
    entryProvider: (Route) -> NavEntry<Route>,
) {
    BackHandler(enabled = state.canGoBack) { onAction(MainScaffoldUiAction.Back) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == state.stacks.selected,
                        onClick = { onAction(MainScaffoldUiAction.SelectTab(tab)) },
                        icon = { Icon(painterResource(tab.iconRes), contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.showRegisterFab) RegisterFab(onClick = { onAction(MainScaffoldUiAction.OpenRegisterSheet) })
        },
    ) { padding ->
        NavDisplay(
            backStack = state.stacks.activeStack,
            onBack = { onAction(MainScaffoldUiAction.Back) },
            entryProvider = { route ->
                if (route == More) {
                    NavEntry(route) {
                        MoreContent(state.moreEntries, onOpen = { onAction(MainScaffoldUiAction.Push(it)) })
                    }
                } else {
                    entryProvider(route)
                }
            },
            modifier = Modifier.padding(padding),
        )
    }

    if (state.registerSheetVisible) {
        RegisterSheet(
            actions = state.registerActions,
            onChoose = { onAction(MainScaffoldUiAction.ChooseRegisterAction(it)) },
            onDismiss = { onAction(MainScaffoldUiAction.DismissRegisterSheet) },
        )
    }
}
