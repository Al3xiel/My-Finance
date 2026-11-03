package com.al3xiel.my_finances.feature.accounts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.AccountCard
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.NameDialog
import com.al3xiel.my_finances.core.designsystem.NoticeDialog
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** S51: cuentas con su saldo; tocar una abre su detalle. */
@Composable
fun AccountsScreen(
    onOpenAccount: (AccountId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountsContent(state = state, onAction = viewModel::onAction, onOpenAccount = onOpenAccount, modifier = modifier)
}

@Composable
fun AccountsContent(
    state: AccountsUiState,
    onAction: (AccountsUiAction) -> Unit,
    onOpenAccount: (AccountId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.accounts_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { onAction(AccountsUiAction.ShowCreate) }) { Text(stringResource(R.string.catalog_add)) }
        }
        if (state.accounts.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.catalog_empty_title),
                body = stringResource(R.string.catalog_empty_body),
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.accounts, key = { it.account.id.value }) { item ->
                    Column {
                        AccountCard(
                            name = item.account.name,
                            balance = Money(item.balanceMinor, item.account.currency),
                            typeLabel = stringResource(item.account.type.labelRes()),
                            isArchived = item.account.isArchived,
                            onClick = { onOpenAccount(item.account.id) },
                        )
                        if (!item.account.isArchived) {
                            Row {
                                TextButton(onClick = { onAction(AccountsUiAction.ShowRename(item.account.id, item.account.name)) }) {
                                    Text(stringResource(R.string.catalog_rename))
                                }
                                TextButton(onClick = { onAction(AccountsUiAction.Archive(item.account.id)) }) {
                                    Text(stringResource(R.string.catalog_archive))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.dialog?.let { AccountDialog(it, onAction) }
    state.notice?.let { NoticeDialog(it, onDismiss = { onAction(AccountsUiAction.DismissNotice) }) }
}

@Composable
private fun AccountDialog(dialog: AccountDialogState, onAction: (AccountsUiAction) -> Unit) {
    NameDialog(
        title = stringResource(if (dialog.renamingId != null) R.string.accounts_rename else R.string.accounts_new),
        name = dialog.name,
        onNameChange = { onAction(AccountsUiAction.EditName(it)) },
        onConfirm = { onAction(AccountsUiAction.Submit) },
        onDismiss = { onAction(AccountsUiAction.DismissDialog) },
        error = dialog.error,
    ) {
        if (dialog.renamingId == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountType.entries.forEach { type ->
                    FilterChip(
                        selected = dialog.type == type,
                        onClick = { onAction(AccountsUiAction.SelectType(type)) },
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }
            MoneyInput(
                value = dialog.openingBalance,
                onValueChange = { onAction(AccountsUiAction.EditOpeningBalance(it)) },
                label = stringResource(R.string.accounts_opening_balance),
            )
        }
    }
}

internal fun AccountType.labelRes() = when (this) {
    AccountType.BANK -> R.string.account_type_bank
    AccountType.CASH -> R.string.account_type_cash
    AccountType.DIGITAL_WALLET -> R.string.account_type_wallet
}

private val previewState = AccountsUiState(
    accounts = listOf(
        AccountWithBalance(Account(AccountId("1"), "BBVA", AccountType.BANK, CurrencyCode.PEN, null, false), 1_095_000),
        AccountWithBalance(Account(AccountId("2"), "Efectivo", AccountType.CASH, CurrencyCode.PEN, null, false), 9_500),
        AccountWithBalance(Account(AccountId("3"), "Antigua", AccountType.BANK, CurrencyCode.PEN, null, true), 0),
    ),
)

@Preview(showBackground = true)
@Composable
private fun AccountsContentPreview() {
    MyfinancesTheme {
        CompositionLocalProvider(LocalHideAmounts provides false) {
            AccountsContent(previewState, onAction = {}, onOpenAccount = {})
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountsContentDarkPreview() {
    MyfinancesTheme {
        CompositionLocalProvider(LocalHideAmounts provides false) {
            AccountsContent(previewState.copy(dialog = AccountDialogState()), onAction = {}, onOpenAccount = {})
        }
    }
}
