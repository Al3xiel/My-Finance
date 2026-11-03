package com.al3xiel.my_finances.feature.onboarding.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.al3xiel.my_finances.core.designsystem.AppIcons
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.message
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** S03: crea las cuentas iniciales con su saldo; al quedar creadas avanza a S04 y esta pantalla no vuelve a mostrarse. */
@Composable
fun AccountsSetupScreen(
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.created) { if (state.created) onCreated() }
    AccountsSetupContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun AccountsSetupContent(
    state: AccountsSetupUiState,
    onAction: (AccountsSetupUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(stringResource(R.string.onboarding_accounts_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.onboarding_accounts_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.rows, key = { it.key }) { row ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = row.name,
                        onValueChange = { onAction(AccountsSetupUiAction.EditName(row.key, it)) },
                        label = { Text(stringResource(R.string.onboarding_account_name)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    )
                    MoneyInput(
                        value = row.balance,
                        onValueChange = { onAction(AccountsSetupUiAction.EditBalance(row.key, it)) },
                        label = stringResource(R.string.onboarding_account_balance),
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(
                        stringResource(R.string.onboarding_account_remove),
                        onClick = { onAction(AccountsSetupUiAction.Remove(row.key)) },
                        style = PillStyle.TEXT,
                    )
                }
            }
            item {
                PillButton(
                    stringResource(R.string.onboarding_account_add),
                    onClick = { onAction(AccountsSetupUiAction.Add) },
                    style = PillStyle.TONAL,
                    icon = AppIcons.Add,
                )
            }
        }
        state.error?.let { error ->
            val text = if (error == BusinessError.InvalidAmount) {
                stringResource(R.string.onboarding_accounts_invalid_balance)
            } else {
                error.message()
            }
            Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        PillButton(
            stringResource(R.string.onboarding_accounts_create),
            onClick = { onAction(AccountsSetupUiAction.Submit) },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountsSetupContentPreview() {
    MyfinancesTheme {
        AccountsSetupContent(
            state = AccountsSetupUiState(
                rows = listOf(
                    AccountRowUi(0, "BBVA", AccountType.BANK, "1250.50"),
                    AccountRowUi(1, "Efectivo", AccountType.CASH),
                ),
            ),
            onAction = {},
        )
    }
}
