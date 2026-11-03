package com.al3xiel.my_finances.feature.home.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.AppIcons
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.MoneyTone
import com.al3xiel.my_finances.core.designsystem.MoneyVariant
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.home.domain.model.HomeAlert
import com.al3xiel.my_finances.feature.home.domain.model.HomeSummary
import com.al3xiel.my_finances.feature.home.domain.rules.HomeFundRules
import com.al3xiel.my_finances.feature.home.ui.components.HomeAlertRow
import com.al3xiel.my_finances.feature.home.ui.components.HomeIndicators
import com.al3xiel.my_finances.feature.home.ui.components.NextIncomeCard

/** S10: los cuatro indicadores, cuentas, fondos activos, próximo ingreso y alertas. */
@Composable
fun HomeScreen(
    onOpenAccount: (AccountId) -> Unit,
    onOpenFund: (FundId) -> Unit,
    onOpenAlert: (HomeAlert) -> Unit,
    onOpenNextIncome: (ExpectedIncomeId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state, viewModel::onAction, onOpenAccount, onOpenFund, onOpenAlert, onOpenNextIncome, modifier)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    onOpenAccount: (AccountId) -> Unit,
    onOpenFund: (FundId) -> Unit,
    onOpenAlert: (HomeAlert) -> Unit,
    onOpenNextIncome: (ExpectedIncomeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        HomeHeader(state.hideAmounts, onToggle = { onAction(HomeUiAction.ToggleHideAmounts) })
        val summary = state.summary
        if (summary == null) {
            val loading = stringResource(R.string.home_loading)
            CircularProgressIndicator(
                modifier = Modifier.padding(32.dp).align(Alignment.CenterHorizontally).semantics { contentDescription = loading },
            )
        } else {
            HomeList(summary, onOpenAccount, onOpenFund, onOpenAlert, onOpenNextIncome)
        }
    }
}

@Composable
private fun HomeHeader(hidden: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        IconButton(onClick = onToggle) {
            val label = stringResource(if (hidden) R.string.home_show_amounts else R.string.home_hide_amounts)
            androidx.compose.material3.Icon(
                painter = painterResource(if (hidden) AppIcons.VisibilityOff else AppIcons.Visibility),
                contentDescription = label,
            )
        }
    }
}

@Composable
private fun HomeList(
    summary: HomeSummary,
    onOpenAccount: (AccountId) -> Unit,
    onOpenFund: (FundId) -> Unit,
    onOpenAlert: (HomeAlert) -> Unit,
    onOpenNextIncome: (ExpectedIncomeId) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (summary.alerts.isNotEmpty()) {
            item { SectionTitle(R.string.home_section_alerts) }
            items(summary.alerts, key = { it.toString() }) { alert -> HomeAlertRow(alert, onClick = { onOpenAlert(alert) }) }
        }
        item {
            HomeIndicators(summary.accountsBalance, summary.reserved, summary.freeAvailable, summary.receivable)
        }
        item { SectionTitle(R.string.home_section_accounts) }
        if (summary.accounts.isEmpty()) {
            item { EmptyLine(R.string.home_empty_accounts) }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(summary.accounts, key = { it.account.id.value }) { line -> AccountRow(line, onOpenAccount) }
                }
            }
        }
        item { SectionTitle(R.string.home_section_funds) }
        if (summary.funds.isEmpty()) {
            item { EmptyLine(R.string.home_empty_funds) }
        } else {
            items(summary.funds, key = { it.fund.id.value }) { fund -> HomeFundRow(fund, onClick = { onOpenFund(fund.fund.id) }) }
        }
        item { SectionTitle(R.string.home_section_next_income) }
        val next = summary.nextIncome
        if (next == null) {
            item { EmptyLine(R.string.home_empty_next_income) }
        } else {
            val overdue = summary.alerts.any { it is HomeAlert.OverdueIncome }
            item { NextIncomeCard(next, overdue = overdue, onClick = { onOpenNextIncome(next.id) }) }
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun EmptyLine(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Ficha del carrusel: el saldo va debajo del nombre para que ninguno se parta con la fuente grande. */
@Composable
private fun AccountRow(line: AccountWithBalance, onOpenAccount: (AccountId) -> Unit) {
    Card(
        onClick = { onOpenAccount(line.account.id) },
        modifier = Modifier.width(200.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(line.account.name, style = MaterialTheme.typography.titleMedium)
            MoneyText(Money(line.balanceMinor, line.account.currency), variant = MoneyVariant.NORMAL, tone = MoneyTone.AUTO)
        }
    }
}

/** La barra muestra lo consumido; «estimado» solo aparece en los modos estimados. */
@Composable
private fun HomeFundRow(overview: FundOverview, onClick: () -> Unit) {
    val consumed = HomeFundRules.consumed(overview)
    val fraction = HomeFundRules.fraction(consumed, overview.fund.allocated)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(overview.fund.name, style = MaterialTheme.typography.titleMedium)
            // La barra es decorativa: las cifras de abajo dicen lo mismo.
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                val label = stringResource(
                    if (HomeFundRules.isEstimated(overview)) R.string.home_fund_consumed_estimated else R.string.home_fund_consumed,
                )
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(consumed, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.home_fund_of), style = MaterialTheme.typography.bodySmall)
                MoneyText(overview.fund.allocated, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeContentPreview() {
    val pen = { minor: Long -> Money(minor, CurrencyCode.PEN) }
    val summary = HomeSummary(
        accountsBalance = pen(1_095_000), reserved = pen(150_000), freeAvailable = pen(945_000), receivable = pen(23_000),
        accounts = emptyList(), funds = emptyList(), nextIncome = null, alerts = listOf(HomeAlert.BackupOverdue),
    )
    CompositionLocalProvider(LocalHideAmounts provides false) {
        HomeContent(HomeUiState(summary), {}, {}, {}, {}, {})
    }
}
