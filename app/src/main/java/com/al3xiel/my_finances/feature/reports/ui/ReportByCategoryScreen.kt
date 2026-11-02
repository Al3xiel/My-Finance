package com.al3xiel.my_finances.feature.reports.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.ChartSlice
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.PieChart
import com.al3xiel.my_finances.core.designsystem.ShareBar
import com.al3xiel.my_finances.core.designsystem.chartPalette
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import java.time.YearMonth

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

/** S41: torta y tabla que suman 100% sobre el gasto propio; tocar una categoría abre sus movimientos. */
@Composable
fun ReportByCategoryScreen(
    from: String,
    to: String,
    onOpenMovements: (categoryId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportByCategoryViewModel = hiltViewModel<ReportByCategoryViewModel, ReportByCategoryViewModel.Factory>(
        key = "$from..$to",
        creationCallback = { it.create(from, to) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportByCategoryContent(state, viewModel::onAction, onOpenMovements, modifier)
}

@Composable
fun ReportByCategoryContent(
    state: ReportByCategoryUiState,
    onAction: (ReportByCategoryUiAction) -> Unit,
    onOpenMovements: (categoryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loaded && state.rows.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.report_by_category_empty_title),
            body = stringResource(R.string.report_by_category_empty_body),
            modifier = modifier,
        )
        return
    }
    val palette = chartPalette()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PieChart(state.rows.mapIndexed { i, r -> ChartSlice(r.name, r.basisPoints, palette[i % palette.size]) })
                Text(stringResource(R.string.report_by_category_total), style = MaterialTheme.typography.labelLarge)
                MoneyText(pen(state.total), style = MaterialTheme.typography.headlineSmall)
            }
        }
        items(state.rows, key = { it.id }) { row ->
            val index = state.rows.indexOf(row)
            val openDescription = stringResource(R.string.report_by_category_open_movements, row.name)
            Column {
                ShareBar(
                    label = row.name,
                    amount = pen(row.amount),
                    basisPoints = row.basisPoints,
                    color = palette[index % palette.size],
                    modifier = Modifier
                        .clickable(role = Role.Button) { onOpenMovements(row.id) }
                        .semantics { contentDescription = openDescription },
                )
                if (row.children.isNotEmpty()) {
                    val expanded = state.expandedId == row.id
                    TextButton(
                        onClick = { onAction(ReportByCategoryUiAction.ToggleDetail(row.id)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(if (expanded) R.string.report_by_category_hide_detail else R.string.report_by_category_show_detail))
                    }
                    if (expanded) {
                        row.children.forEach { child ->
                            ShareBar(
                                label = child.name,
                                amount = pen(child.amount),
                                basisPoints = child.basisPoints,
                                color = palette[index % palette.size],
                                modifier = Modifier.padding(start = 16.dp).clickable(role = Role.Button) { onOpenMovements(child.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportByCategoryContentPreview() {
    ReportByCategoryContent(
        state = ReportByCategoryUiState(
            period = ReportPeriod.Month(YearMonth.of(2026, 10)),
            rows = listOf(
                CategoryRowUi("food", "Comida", 60_000, 6_000, listOf(CategoryRowUi("mkt", "Mercado", 40_000, 6_667))),
                CategoryRowUi("home", "Hogar", 40_000, 4_000),
            ),
            total = 100_000, expandedId = "food", loaded = true,
        ),
        onAction = {}, onOpenMovements = {},
    )
}
