package com.al3xiel.my_finances.feature.reports.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.EstimatedBadge
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.SankeyBox
import com.al3xiel.my_finances.core.designsystem.SankeyDiagram
import com.al3xiel.my_finances.core.designsystem.SankeyFlow
import com.al3xiel.my_finances.core.designsystem.ShareBar
import com.al3xiel.my_finances.core.designsystem.chartPalette
import com.al3xiel.my_finances.core.money.MoneyFormatter
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyLink
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyNode
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyNodeKind
import com.al3xiel.my_finances.feature.reports.ui.components.reportMoney
import java.time.YearMonth

/** S44: composición del saldo por fuente, "¿en qué se gastó lo de X?" y el Sankey del periodo; todo rotulado estimado. */
@Composable
fun ReportOriginScreen(
    from: String,
    to: String,
    modifier: Modifier = Modifier,
    viewModel: ReportOriginViewModel = hiltViewModel<ReportOriginViewModel, ReportOriginViewModel.Factory>(
        key = "origin:$from..$to",
        creationCallback = { it.create(from, to) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportOriginContent(state, viewModel::onAction, modifier)
}

@Composable
fun ReportOriginContent(
    state: ReportOriginUiState,
    onAction: (ReportOriginUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loaded && state.accounts.isEmpty() && state.sankey.links.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.report_origin_empty_title),
            body = stringResource(R.string.report_origin_empty_body),
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EstimatedBadge()
                Text(stringResource(R.string.report_origin_estimate_note), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.report_origin_funds_note), style = MaterialTheme.typography.bodyMedium)
                if (state.anyShortfall) {
                    Text(
                        stringResource(R.string.report_origin_shortfall_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item { SectionTitle(stringResource(R.string.report_origin_composition_title)) }
        items(state.accounts, key = { "account:${it.accountId}" }) { AccountCard(it) }
        if (state.sourceOptions.isNotEmpty()) item { SpendSection(state, onAction) }
        if (state.sankey.links.isNotEmpty()) item { SankeySection(state.sankey) }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun AccountCard(account: OriginAccountUi) {
    val palette = chartPalette()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(account.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                EstimatedBadge()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.report_origin_balance), style = MaterialTheme.typography.bodyMedium)
                MoneyText(reportMoney(account.balanceMinor), style = MaterialTheme.typography.bodyMedium)
            }
            if (account.hasShortfall) {
                Text(stringResource(R.string.report_origin_shortfall_account), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
            account.slices.forEachIndexed { index, slice ->
                ShareBar(slice.name, reportMoney(slice.amountMinor), slice.basisPoints, palette[index % palette.size])
            }
        }
    }
}

@Composable
private fun SpendSection(state: ReportOriginUiState, onAction: (ReportOriginUiAction) -> Unit) {
    val selected = state.sourceOptions.firstOrNull { it.id == state.selectedSourceId }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.report_origin_spend_title, selected?.name.orEmpty()))
        EstimatedBadge()
        Text(stringResource(R.string.report_origin_spend_pick), style = MaterialTheme.typography.labelLarge)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.sourceOptions.forEach { option ->
                FilterChip(
                    selected = option.id == state.selectedSourceId,
                    onClick = { onAction(ReportOriginUiAction.SelectSource(option.id)) },
                    label = { Text(option.name) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        state.spend.forEach { row ->
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.report_origin_spend_row, targetName(row.categoryName, row.kind), row.accountName),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                MoneyText(reportMoney(row.amountMinor), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun targetName(categoryName: String?, kind: LedgerEntryKind): String = categoryName ?: stringResource(
    if (kind == LedgerEntryKind.LOAN_OUT) R.string.report_origin_target_loans else R.string.report_origin_target_unidentified,
)

@Composable
private fun SankeySection(sankey: SankeyUi) {
    val loans = stringResource(R.string.report_origin_target_loans)
    val unidentified = stringResource(R.string.report_origin_target_unidentified)
    val hidden = LocalHideAmounts.current
    val columns = sankey.columns.map { column ->
        column.map { box ->
            SankeyBox(
                key = nodeKey(box.node),
                label = box.label ?: if (box.node.kind == SankeyNodeKind.LOANS) loans else unidentified,
                valueMinor = box.valueMinor,
            )
        }
    }
    val flows = sankey.links.map { SankeyFlow(nodeKey(it.from), nodeKey(it.to), it.amountMinor) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.report_origin_sankey_title))
        EstimatedBadge()
        Text(stringResource(R.string.report_origin_sankey_note), style = MaterialTheme.typography.bodyMedium)
        SankeyDiagram(columns, flows, amountLabel = { if (hidden) "••••" else MoneyFormatter.format(reportMoney(it)) })
    }
}

private fun nodeKey(node: SankeyNode) = "${node.kind}:${node.id}"

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportOriginContentPreview() {
    val source = SankeyNode(SankeyNodeKind.SOURCE, "s1")
    val account = SankeyNode(SankeyNodeKind.ACCOUNT, "bbva")
    val category = SankeyNode(SankeyNodeKind.CATEGORY, "food")
    ReportOriginContent(
        state = ReportOriginUiState(
            period = ReportPeriod.Month(YearMonth.of(2026, 10)),
            accounts = listOf(
                OriginAccountUi("bbva", "BBVA", 100_000, listOf(OriginSliceUi("s1", "Sueldo", 80_000, 8_000), OriginSliceUi("s2", "Freelance", 20_000, 2_000)), false),
            ),
            sourceOptions = listOf(OriginSourceOptionUi("s1", "Sueldo", 50_000)),
            selectedSourceId = "s1",
            spend = listOf(SpendRowUi("BBVA", "Comida", LedgerEntryKind.EXPENSE, 50_000)),
            sankey = SankeyUi(
                columns = listOf(
                    listOf(SankeyBoxUi(source, "Sueldo", 50_000)),
                    listOf(SankeyBoxUi(account, "BBVA", 50_000)),
                    listOf(SankeyBoxUi(category, "Comida", 50_000)),
                ),
                links = listOf(SankeyLink(source, account, 50_000), SankeyLink(account, category, 50_000)),
            ),
            loaded = true,
        ),
        onAction = {},
    )
}
