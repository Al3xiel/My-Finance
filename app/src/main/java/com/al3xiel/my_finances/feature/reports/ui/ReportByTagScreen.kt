package com.al3xiel.my_finances.feature.reports.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.feature.reports.ui.components.reportMoney
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import java.time.YearMonth

/** S42: total por concepto por separado, sin torta (los conceptos no suman); el cruce con categoría se abre por concepto. */
@Composable
fun ReportByTagScreen(
    from: String,
    to: String,
    onOpenMovements: (tagId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportByTagViewModel = hiltViewModel<ReportByTagViewModel, ReportByTagViewModel.Factory>(
        key = "$from..$to",
        creationCallback = { it.create(from, to) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportByTagContent(state, viewModel::onAction, onOpenMovements, modifier)
}

@Composable
fun ReportByTagContent(
    state: ReportByTagUiState,
    onAction: (ReportByTagUiAction) -> Unit,
    onOpenMovements: (tagId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loaded && state.rows.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.report_by_tag_empty_title),
            body = stringResource(R.string.report_by_tag_empty_body),
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                stringResource(R.string.report_by_tag_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.rows, key = { it.id }) { row ->
            val expanded = state.expandedId == row.id
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onOpenMovements(row.id) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        MoneyText(reportMoney(row.amount), style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(onClick = { onAction(ReportByTagUiAction.ToggleCrossing(row.id)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(if (expanded) R.string.report_by_tag_hide_crossing else R.string.report_by_tag_show_crossing))
                    }
                    if (expanded) {
                        row.byCategory.forEach { (category, amount) ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(category, style = MaterialTheme.typography.bodyMedium)
                                MoneyText(reportMoney(amount), style = MaterialTheme.typography.bodyMedium)
                            }
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
private fun ReportByTagContentPreview() {
    ReportByTagContent(
        state = ReportByTagUiState(
            period = ReportPeriod.Month(YearMonth.of(2026, 10)),
            rows = listOf(TagRowUi("t1", "Viaje", 80_000, listOf("Comida" to 50_000L, "Transporte" to 30_000L)), TagRowUi("t2", "Trabajo", 20_000, emptyList())),
            expandedId = "t1", loaded = true,
        ),
        onAction = {}, onOpenMovements = {},
    )
}
