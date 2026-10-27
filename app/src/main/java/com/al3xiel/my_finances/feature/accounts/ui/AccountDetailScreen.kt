package com.al3xiel.my_finances.feature.accounts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.DateChooserDialog
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneySign
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.accounts.domain.model.LedgerLine
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** S51 (detalle): saldo, saldo a una fecha y asientos de la cuenta. */
@Composable
fun AccountDetailScreen(
    accountId: AccountId,
    modifier: Modifier = Modifier,
    viewModel: AccountDetailViewModel = hiltViewModel<AccountDetailViewModel, AccountDetailViewModel.Factory>(
        key = accountId.value,
        creationCallback = { it.create(accountId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountDetailContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun AccountDetailContent(
    state: AccountDetailUiState,
    onAction: (AccountDetailUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = state.account
    if (account == null) {
        EmptyState(
            title = stringResource(R.string.error_not_found),
            body = "",
            modifier = modifier,
        )
        return
    }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account.account.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.account_detail_balance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyText(Money(account.balanceMinor, account.account.currency), style = MaterialTheme.typography.headlineMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.asOf != null && state.balanceAsOfMinor != null) {
                        Text(
                            stringResource(R.string.account_detail_balance_at, dayText(state.asOf)),
                            modifier = Modifier.weight(1f),
                        )
                        MoneyText(Money(state.balanceAsOfMinor, account.account.currency))
                        TextButton(onClick = { onAction(AccountDetailUiAction.ClearAsOf) }) {
                            Text(stringResource(R.string.movements_filter_clear))
                        }
                    } else {
                        TextButton(onClick = { pickingDate = true }) { Text(stringResource(R.string.account_detail_pick_date)) }
                    }
                }
                Text(
                    stringResource(R.string.account_detail_ledger),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            }
        }
        if (state.ledger.isEmpty()) {
            item { Text(stringResource(R.string.account_detail_no_ledger), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.ledger, key = { it.id }) { line -> LedgerRow(line, account.account.currency) }
        }
    }
    if (pickingDate) {
        DateChooserDialog(
            onDateSelected = {
                pickingDate = false
                onAction(AccountDetailUiAction.PickAsOf(it))
            },
            onDismiss = { pickingDate = false },
            maxDate = state.today,
        )
    }
}

@Composable
private fun LedgerRow(line: LedgerLine, currency: CurrencyCode) {
    val strike = if (line.isVoided) TextDecoration.LineThrough else null
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(line.kind.labelRes()), style = MaterialTheme.typography.bodyLarge, textDecoration = strike)
            val subtitle = dayText(line.occurredOn) + if (line.isVoided) " · " + stringResource(R.string.ledger_voided) else ""
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoneyText(
            money = Money(kotlin.math.abs(line.deltaMinor), currency),
            sign = if (line.deltaMinor >= 0) MoneySign.PLUS else MoneySign.MINUS,
            textDecoration = strike,
        )
    }
}

private fun LedgerEntryKind.labelRes() = when (this) {
    LedgerEntryKind.OPENING -> R.string.ledger_opening
    LedgerEntryKind.INCOME -> R.string.ledger_income
    LedgerEntryKind.EXPENSE -> R.string.ledger_expense
    LedgerEntryKind.FEE -> R.string.ledger_fee
    LedgerEntryKind.TRANSFER_OUT -> R.string.ledger_transfer_out
    LedgerEntryKind.TRANSFER_IN -> R.string.ledger_transfer_in
}

private val previewState = AccountDetailUiState(
    account = AccountWithBalance(Account(AccountId("1"), "BBVA", AccountType.BANK, CurrencyCode.PEN, null, false), 1_095_000),
    ledger = listOf(
        LedgerLine("3-0", 3, null, -30_000, LedgerEntryKind.EXPENSE, LocalDate.of(2026, 10, 5), false),
        LedgerLine("2-0", 2, null, -5_000, LedgerEntryKind.FEE, LocalDate.of(2026, 10, 3), true),
        LedgerLine("1-0", 1, null, 1_100_000, LedgerEntryKind.OPENING, LocalDate.of(2026, 10, 1), false),
    ),
    asOf = LocalDate.of(2026, 10, 4),
    balanceAsOfMinor = 1_100_000,
    today = LocalDate.of(2026, 10, 26),
)

@Preview(showBackground = true)
@Composable
private fun AccountDetailContentPreview() {
    MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { AccountDetailContent(previewState, {}) } }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountDetailContentDarkPreview() {
    MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides true) { AccountDetailContent(previewState, {}) } }
}
