package com.al3xiel.my_finances.feature.reconciliation.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EditorFooter
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.reconciliation.ui.components.DifferenceText
import com.al3xiel.my_finances.feature.reconciliation.ui.components.LabeledMoney

/** S60: "¿Cuánto dice tu banco?". `onOpen` lleva a S61, ya sea la recién registrada o la que la cuenta ya tenía abierta. */
@Composable
fun ReconciliationStartScreen(
    accountId: AccountId,
    onOpen: (ReconciliationId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReconciliationStartViewModel = hiltViewModel<ReconciliationStartViewModel, ReconciliationStartViewModel.Factory>(
        key = accountId.value,
        creationCallback = { it.create(accountId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val next = state.recordedId ?: state.openReconciliationId
    LaunchedEffect(next) { next?.let(onOpen) }
    ReconciliationStartContent(state, viewModel::onAction, modifier)
}

@Composable
fun ReconciliationStartContent(
    state: ReconciliationStartUiState,
    onAction: (ReconciliationStartUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.notFound) {
        EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = modifier)
        return
    }
    val appBalance = state.appBalance ?: return
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.reconciliation_start_title, state.accountName.orEmpty()), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.reconciliation_start_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LabeledMoney(stringResource(R.string.reconciliation_start_app_balance), appBalance)
        MoneyInput(
            value = state.realText,
            onValueChange = { onAction(ReconciliationStartUiAction.EditReal(it)) },
            label = stringResource(R.string.reconciliation_start_real),
            currency = state.currency,
            allowNegative = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.difference?.let {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reconciliation_start_difference), style = MaterialTheme.typography.bodyMedium)
                DifferenceText(it, style = MaterialTheme.typography.titleMedium)
            }
        }
        EditorFooter(error = state.error, onSave = { onAction(ReconciliationStartUiAction.Confirm) })
    }
}

private val previewState = ReconciliationStartUiState(
    loaded = true,
    accountName = "BCP",
    appBalance = Money(80_000, CurrencyCode.PEN),
    realText = "812.40",
    difference = Money(1_240, CurrencyCode.PEN),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReconciliationStartContentPreview() {
    CompositionLocalProvider(LocalHideAmounts provides false) { ReconciliationStartContent(previewState, {}) }
}
