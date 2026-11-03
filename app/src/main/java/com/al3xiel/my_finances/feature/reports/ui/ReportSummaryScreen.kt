package com.al3xiel.my_finances.feature.reports.ui

import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.PillButton
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.MoneySign
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.feature.reports.ui.components.PeriodSelector
import com.al3xiel.my_finances.feature.reports.ui.components.reportMoney
import com.al3xiel.my_finances.feature.reports.domain.model.OthersSpend
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.model.ReportSummary
import java.time.LocalDate
import java.time.YearMonth

/** S40: resumen del periodo. Los accesos a S41–S44 y a la exportación reciben el periodo elegido. */
@Composable
fun ReportSummaryScreen(
    onOpenByCategory: (ReportPeriod) -> Unit,
    onOpenByTag: (ReportPeriod) -> Unit,
    onOpenByAccount: (ReportPeriod) -> Unit,
    onOpenOrigin: (ReportPeriod) -> Unit,
    onExport: (ReportPeriod) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(ReportSummaryUiAction.Refresh)
        onPauseOrDispose { }
    }
    ReportSummaryContent(state, viewModel::onAction, onOpenByCategory, onOpenByTag, onOpenByAccount, onOpenOrigin, onExport, modifier)
}

@Composable
fun ReportSummaryContent(
    state: ReportSummaryUiState,
    onAction: (ReportSummaryUiAction) -> Unit,
    onOpenByCategory: (ReportPeriod) -> Unit,
    onOpenByTag: (ReportPeriod) -> Unit,
    onOpenByAccount: (ReportPeriod) -> Unit,
    onOpenOrigin: (ReportPeriod) -> Unit,
    onExport: (ReportPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    ScreenScaffold(title = stringResource(R.string.report_summary_title), showBack = false, modifier = modifier) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            PeriodSelector(state.period, state.today, onPeriodChange = { onAction(ReportSummaryUiAction.SetPeriod(it)) })
            val summary = state.summary
            if (summary == null) {
                Text(stringResource(R.string.report_summary_loading), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            FigureCard(stringResource(R.string.report_summary_income), summary.income, null, sign = MoneySign.NONE)
            FigureCard(stringResource(R.string.report_summary_own_spend), summary.ownSpend, stringResource(R.string.report_summary_own_spend_hint))
            OthersCard(summary.others)
            if (summary.unidentified != 0L) {
                FigureCard(
                    stringResource(R.string.report_summary_unidentified),
                    summary.unidentified,
                    stringResource(R.string.report_summary_unidentified_hint),
                    sign = if (summary.unidentified > 0) MoneySign.PLUS else MoneySign.NONE,
                )
            }
            SectionCard {
                val entries = listOf(
                    R.string.report_summary_by_category to onOpenByCategory,
                    R.string.report_summary_by_tag to onOpenByTag,
                    R.string.report_summary_by_account to onOpenByAccount,
                    R.string.report_summary_origin to onOpenOrigin,
                )
                entries.forEachIndexed { index, (label, open) ->
                    if (index > 0) SectionDivider()
                    SectionRow(headline = stringResource(label), onClick = { open(state.period) })
                }
            }
            PillButton(stringResource(R.string.report_summary_export), { onExport(state.period) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FigureCard(label: String, minor: Long, hint: String?, sign: MoneySign = MoneySign.NONE) {
    SectionCard {
        Column(Modifier.padding(LocalSpacing.current.lg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(reportMoney(minor), style = MaterialTheme.typography.headlineMedium, sign = sign)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OthersCard(others: OthersSpend) {
    SectionCard {
        Column(Modifier.padding(LocalSpacing.current.lg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.report_summary_others),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OthersLine(R.string.report_summary_others_total, others.total)
            OthersLine(R.string.report_summary_others_reimbursed, others.reimbursed)
            OthersLine(R.string.report_summary_others_pending, others.pending)
        }
    }
}

@Composable
private fun OthersLine(label: Int, minor: Long) {
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        MoneyText(reportMoney(minor), style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ReportSummaryContentPreview() {
    MyfinancesTheme { ReportSummaryContent(
        state = ReportSummaryUiState(
            period = ReportPeriod.Month(YearMonth.of(2026, 10)),
            today = LocalDate.of(2026, 11, 2),
            summary = ReportSummary(500_000, 320_000, OthersSpend(120_000, 20_000), -3_000),
        ),
        onAction = {}, onOpenByCategory = {}, onOpenByTag = {}, onOpenByAccount = {}, onOpenOrigin = {}, onExport = {},
    ) }
}
