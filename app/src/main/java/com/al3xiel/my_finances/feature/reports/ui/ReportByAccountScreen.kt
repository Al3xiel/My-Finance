package com.al3xiel.my_finances.feature.reports.ui

import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import com.al3xiel.my_finances.core.designsystem.SectionCard
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.MoneySign
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.feature.reports.ui.components.ReportList
import com.al3xiel.my_finances.feature.reports.ui.components.reportMoney
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import java.time.YearMonth

/** S43: entradas, salidas y transferencias netas (aparte) de cada cuenta; tocar una abre sus movimientos. */
@Composable
fun ReportByAccountScreen(
    from: String,
    to: String,
    onOpenMovements: (accountId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportByAccountViewModel = hiltViewModel<ReportByAccountViewModel, ReportByAccountViewModel.Factory>(
        key = "$from..$to",
        creationCallback = { it.create(from, to) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportByAccountContent(state, onOpenMovements, modifier)
}

@Composable
fun ReportByAccountContent(
    state: ReportByAccountUiState,
    onOpenMovements: (accountId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ReportList(
        title = stringResource(R.string.report_by_account_title),
        isEmpty = state.loaded && state.rows.isEmpty(),
        emptyTitle = stringResource(R.string.report_by_account_empty_title),
        emptyBody = stringResource(R.string.report_by_account_empty_body),
        modifier = modifier,
    ) {
        items(state.rows, key = { it.id }) { row ->
            SectionCard(Modifier.clickable(role = Role.Button) { onOpenMovements(row.id) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    FlowLine(R.string.report_by_account_inflow, row.inflow, MoneySign.PLUS)
                    FlowLine(R.string.report_by_account_outflow, row.outflow, MoneySign.MINUS)
                    FlowLine(
                        R.string.report_by_account_transfers, Math.abs(row.transfersNet),
                        if (row.transfersNet >= 0) MoneySign.PLUS else MoneySign.MINUS,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowLine(label: Int, minor: Long, sign: MoneySign) {
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        MoneyText(reportMoney(minor), sign = if (minor == 0L) MoneySign.NONE else sign, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ReportByAccountContentPreview() {
    MyfinancesTheme { ReportByAccountContent(
        state = ReportByAccountUiState(
            period = ReportPeriod.Month(YearMonth.of(2026, 10)),
            rows = listOf(AccountFlowRowUi("bbva", "BBVA", 500_000, 120_000, -10_000), AccountFlowRowUi("cash", "Efectivo", 0, 5_000, 10_000)),
            loaded = true,
        ),
        onOpenMovements = {},
    ) }
}
