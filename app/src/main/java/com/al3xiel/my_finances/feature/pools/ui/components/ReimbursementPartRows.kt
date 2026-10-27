package com.al3xiel.my_finances.feature.pools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.feature.pools.domain.model.PendingPartLine
import com.al3xiel.my_finances.feature.pools.ui.ReimbursementEditorUiAction
import com.al3xiel.my_finances.feature.pools.ui.ReimbursementEditorUiState
import com.al3xiel.my_finances.feature.pools.ui.ReimbursementLineUi
import java.time.LocalDate

@Composable
fun PendingPartsSection(state: ReimbursementEditorUiState, onAction: (ReimbursementEditorUiAction) -> Unit) {
    Text(stringResource(R.string.reimbursement_parts), style = MaterialTheme.typography.titleMedium)
    if (state.lines.isEmpty()) {
        Text(stringResource(R.string.reimbursement_nothing_pending), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.reimbursement_pending_total), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(state.pendingTotal)
    }
    if (state.exceedsPending) {
        Text(stringResource(R.string.error_exceeds_pending), color = MaterialTheme.colorScheme.error)
    }
    state.lines.forEach { row ->
        PendingPartRow(row, manual = state.strategy == AllocationStrategy.MANUAL, onAction = onAction)
    }
    if (state.strategy == AllocationStrategy.MANUAL) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reimbursement_unassigned), modifier = Modifier.weight(1f))
            MoneyText(state.unassigned)
        }
    }
}

@Composable
private fun PendingPartRow(row: ReimbursementLineUi, manual: Boolean, onAction: (ReimbursementEditorUiAction) -> Unit) {
    val line = row.line
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text((listOf(line.categoryName) + line.tagNames).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${line.poolName} · ${dayText(line.occurredOn)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.reimbursement_part_pending), style = MaterialTheme.typography.bodySmall)
                MoneyText(line.pending, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (manual) {
            MoneyInput(
                value = row.manualText,
                onValueChange = { onAction(ReimbursementEditorUiAction.EditManual(line.partId, it)) },
                label = stringResource(R.string.reimbursement_part_settles),
                modifier = Modifier.weight(0.8f),
            )
        } else if (row.willSettle.isPositive) {
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.reimbursement_part_settles), style = MaterialTheme.typography.labelSmall)
                MoneyText(row.willSettle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PendingPartsSectionPreview() {
    val pen = { minor: Long -> Money(minor, CurrencyCode.PEN) }
    Column {
        PendingPartsSection(
            ReimbursementEditorUiState(
                loaded = true,
                lines = listOf(
                    ReimbursementLineUi(
                        PendingPartLine(PartId("p1"), PoolId("dogs"), "Perros – mamá", LocalDate.of(2026, 10, 3), 4, "Mascotas", listOf("Perros"), pen(12_000)),
                        pen(12_000), "",
                    ),
                ),
                pendingTotal = pen(12_000),
                strategy = AllocationStrategy.OLDEST_FIRST,
            ),
            onAction = {},
        )
    }
}
