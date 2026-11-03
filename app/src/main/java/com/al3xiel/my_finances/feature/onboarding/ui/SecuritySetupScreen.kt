package com.al3xiel.my_finances.feature.onboarding.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** S04: revisa el bloqueo y «ocultar en recientes»; al guardar avanza a S05. */
@Composable
fun SecuritySetupScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecuritySetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) {
            onSaved()
            viewModel.onAction(SecuritySetupUiAction.SavedHandled)
        }
    }
    SecuritySetupContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun SecuritySetupContent(
    state: SecuritySetupUiState,
    onAction: (SecuritySetupUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onboarding_security_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onboarding_security_body), style = MaterialTheme.typography.bodyMedium)
        SwitchRow(
            label = stringResource(R.string.privacy_lock),
            checked = state.lockEnabled,
            enabled = state.lockAvailable,
            onCheckedChange = { onAction(SecuritySetupUiAction.SetLock(it)) },
        )
        if (!state.lockAvailable) {
            Text(stringResource(R.string.privacy_no_credential), color = MaterialTheme.colorScheme.error)
        }
        SwitchRow(
            label = stringResource(R.string.privacy_secure_screen),
            checked = state.secureScreen,
            enabled = true,
            onCheckedChange = { onAction(SecuritySetupUiAction.SetSecureScreen(it)) },
        )
        Button(onClick = { onAction(SecuritySetupUiAction.Save) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SecuritySetupContentPreview() {
    MyfinancesTheme { SecuritySetupContent(SecuritySetupUiState(lockAvailable = false, lockEnabled = false), onAction = {}) }
}
