package com.al3xiel.my_finances.feature.onboarding.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** S02: qué hace la app y que los datos no salen del teléfono; restaurar solo si no hay datos propios. */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WelcomeContent(state = state, onContinue = onContinue, onRestore = onRestore, modifier = modifier)
}

@Composable
fun WelcomeContent(
    state: WelcomeUiState,
    onContinue: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, alignment = Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.onboarding_welcome_privacy), style = MaterialTheme.typography.bodyMedium)
        PillButton(stringResource(R.string.onboarding_continue), onClick = onContinue, modifier = Modifier.fillMaxWidth())
        if (state.canRestore) {
            PillButton(stringResource(R.string.onboarding_restore), onClick = onRestore, style = PillStyle.TONAL, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WelcomeContentPreview() {
    MyfinancesTheme { WelcomeContent(WelcomeUiState(canRestore = true), onContinue = {}, onRestore = {}) }
}
