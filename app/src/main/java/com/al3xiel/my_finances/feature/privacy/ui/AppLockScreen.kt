package com.al3xiel.my_finances.feature.privacy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** Puerta: mientras la app está bloqueada, [content] no se compone. */
@Composable
fun AppLockGate(
    modifier: Modifier = Modifier,
    viewModel: AppLockViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val prompt = unlockPrompt()

    DisposableEffect(Unit) {
        val processLifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAction(AppLockUiAction.AppBackgrounded)
                Lifecycle.Event.ON_START -> viewModel.onAction(AppLockUiAction.AppForegrounded)
                else -> Unit
            }
        }
        processLifecycle.addObserver(observer)
        onDispose { processLifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.isLocked) {
        if (!state.isLocked) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onAction(AppLockUiAction.Unlock(prompt))
        }
    }

    if (state.isLocked) {
        AppLockContent(state = state, onAction = viewModel::onAction, modifier = modifier)
    } else {
        content()
    }
}

@Composable
private fun unlockPrompt() = AuthPrompt(title = stringResource(R.string.app_lock_prompt_title))

@Composable
fun AppLockContent(
    state: AppLockUiState,
    onAction: (AppLockUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prompt = unlockPrompt()
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(R.string.app_lock_title), style = MaterialTheme.typography.headlineMedium)
            state.message?.let { Text(text = stringResource(it.textRes())) }
            Button(onClick = { onAction(AppLockUiAction.Unlock(prompt)) }) {
                Text(stringResource(R.string.app_lock_unlock))
            }
        }
    }
}

private fun AppLockMessage.textRes() = when (this) {
    AppLockMessage.LOCKED_OUT -> R.string.app_lock_message_locked_out
    AppLockMessage.FAILED -> R.string.app_lock_message_failed
    AppLockMessage.NOT_AVAILABLE -> R.string.app_lock_message_not_available
}

@Preview(showBackground = true)
@Composable
private fun AppLockContentPreview() {
    MyfinancesTheme { AppLockContent(AppLockUiState(message = AppLockMessage.FAILED), onAction = {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppLockContentDarkPreview() {
    MyfinancesTheme { AppLockContent(AppLockUiState(), onAction = {}) }
}
