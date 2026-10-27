package com.al3xiel.my_finances.feature.pools.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.message
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.feature.pools.domain.model.SettlementStatementContent
import com.al3xiel.my_finances.feature.pools.domain.model.StatementLine
import com.al3xiel.my_finances.feature.pools.ui.components.SettlementConfirmDialog
import com.al3xiel.my_finances.feature.pools.ui.components.StatementLineRow
import com.al3xiel.my_finances.feature.pools.ui.components.TotalRow
import java.time.LocalDate

/** S33 (D17): vista previa de la rendición, formato, aviso de cierre, y luego compartir como imagen o PDF. */
@Composable
fun SettlementStatementScreen(
    poolId: PoolId,
    modifier: Modifier = Modifier,
    viewModel: SettlementStatementViewModel = hiltViewModel<SettlementStatementViewModel, SettlementStatementViewModel.Factory>(
        key = poolId.value,
        creationCallback = { it.create(poolId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var waitingForShare by rememberSaveable { mutableStateOf(false) }

    // Compartir no emite eventos: el archivo se dibuja fuera del hilo principal y se abre el selector de apps.
    LaunchedEffect(state.shareRequest) {
        val request = state.shareRequest ?: return@LaunchedEffect
        val intent = viewModel.shareIntent(request)
        viewModel.onAction(SettlementStatementUiAction.ShareHandled)
        if (intent != null) {
            waitingForShare = true
            context.startActivity(intent)
        }
    }
    // Al volver del selector la otra app ya recibió el archivo: se borra (docs/02 S7).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (waitingForShare) {
            waitingForShare = false
            viewModel.onAction(SettlementStatementUiAction.ShareClosed)
        }
    }
    SettlementStatementScreenContent(state, viewModel::onAction, modifier)
}

@Composable
fun SettlementStatementScreenContent(
    state: SettlementStatementUiState,
    onAction: (SettlementStatementUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state.content
    if (content == null) {
        if (state.loaded) {
            EmptyState(
                title = state.error?.message() ?: stringResource(R.string.error_not_found),
                body = "",
                modifier = modifier,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(content.poolName, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.statement_responsible, content.responsibleAlias),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.statement_period, dayText(content.periodFrom), dayText(content.generatedOn)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(content.lines, key = { it.partId.value }) { line -> StatementLineRow(line) }
        item {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalDivider()
                TotalRow(stringResource(R.string.statement_period_pending), content.pendingTotal)
                if (content.previousPending.isPositive) {
                    TotalRow(stringResource(R.string.statement_previous_pending), content.previousPending)
                }
                TotalRow(stringResource(R.string.statement_grand_total), content.grandTotal, emphasis = true)
            }
        }
        item {
            Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.statement_format), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatementFormat.entries.forEach { format ->
                        FilterChip(
                            selected = state.format == format,
                            onClick = { onAction(SettlementStatementUiAction.SelectFormat(format)) },
                            label = { Text(stringResource(format.labelRes())) },
                        )
                    }
                }
                if (state.generated == null) {
                    Button(onClick = { onAction(SettlementStatementUiAction.AskConfirm) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.statement_share))
                    }
                } else {
                    Button(onClick = { onAction(SettlementStatementUiAction.ShareAgain) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.statement_share_again))
                    }
                }
                state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (state.confirming) {
        SettlementConfirmDialog(
            kind = state.kind,
            onConfirm = { onAction(SettlementStatementUiAction.Confirm) },
            onDismiss = { onAction(SettlementStatementUiAction.DismissConfirm) },
        )
    }
}

private fun StatementFormat.labelRes() = when (this) {
    StatementFormat.IMAGE -> R.string.statement_format_image
    StatementFormat.PDF -> R.string.statement_format_pdf
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private val previewContent = SettlementStatementContent(
    PoolId("dogs"), PoolPeriodId("dogs-p1"), "Perros – mamá", "Mamá", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
    listOf(
        StatementLine(PartId("p1"), LocalDate.of(2026, 10, 3), "Mascotas", listOf("Perros"), pen(12_000), pen(12_000)),
        StatementLine(PartId("p2"), LocalDate.of(2026, 10, 12), "Comida", emptyList(), pen(8_000), pen(5_000)),
    ),
    pen(17_000), pen(30_000),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettlementStatementContentPreview() {
    SettlementStatementScreenContent(SettlementStatementUiState(loaded = true, content = previewContent, kind = PoolKind.PERMANENT), {})
}

@Preview(showBackground = true)
@Composable
private fun SettlementStatementConfirmPreview() {
    SettlementStatementScreenContent(
        SettlementStatementUiState(loaded = true, content = previewContent, kind = PoolKind.PERMANENT, confirming = true),
        {},
    )
}
