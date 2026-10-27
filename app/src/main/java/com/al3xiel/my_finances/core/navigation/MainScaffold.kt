package com.al3xiel.my_finances.core.navigation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
    val navigator = remember(onAction) {
        object : Navigator {
            override fun push(route: Route) = onAction(MainScaffoldUiAction.Push(route))

            override fun back() = onAction(MainScaffoldUiAction.Back)
        }
    }

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
        CompositionLocalProvider(LocalNavigator provides navigator) {
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
    }

    if (state.registerSheetVisible) {
        RegisterSheet(
            actions = state.registerActions,
            onChoose = { onAction(MainScaffoldUiAction.ChooseRegisterAction(it)) },
            onDismiss = { onAction(MainScaffoldUiAction.DismissRegisterSheet) },
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MainScaffoldContentPreview() {
    MainScaffoldContent(MainScaffoldUiState(), onAction = {}, entryProvider = rememberRouteEntryProvider(emptySet()))
}
