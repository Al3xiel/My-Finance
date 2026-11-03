package com.al3xiel.my_finances.feature.funds.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.FundProgressCard
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.feature.funds.domain.model.Fund
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.model.FundSummary
import java.time.LocalDate

/** S52 (lista): lo apartado, lo que queda libre y una tarjeta por fondo. */
@Composable
fun FundsScreen(
    onOpenFund: (FundId) -> Unit,
    onCreateFund: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FundsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FundsContent(state, onOpenFund, onCreateFund, modifier)
}

@Composable
fun FundsContent(
    state: FundsUiState,
    onOpenFund: (FundId) -> Unit,
    onCreateFund: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loaded && state.funds.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.funds_empty_title),
            body = stringResource(R.string.funds_empty_body),
            actionLabel = stringResource(R.string.funds_create),
            onAction = onCreateFund,
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SummaryRow(state.summary) }
        item {
            Button(onClick = onCreateFund, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.funds_create)) }
        }
        items(state.funds, key = { it.fund.id.value }) { overview ->
            FundProgressCard(
                name = overview.fund.name,
                allocated = overview.fund.allocated,
                spent = overview.detailedSpent + overview.fund.untrackedSpent,
                remaining = overview.estimatedRemaining,
                isClosed = overview.fund.status == FundStatus.CLOSED,
                onClick = { onOpenFund(overview.fund.id) },
            )
        }
    }
}

@Composable
private fun SummaryRow(summary: FundSummary) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                stringResource(R.string.funds_summary_reserved),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(summary.reserved, style = MaterialTheme.typography.titleLarge)
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(
                stringResource(R.string.funds_summary_available),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(summary.available, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private val previewState = run {
    fun overview(id: String, name: String, allocated: Long, detailed: Long, status: FundStatus = FundStatus.ACTIVE) = FundOverview(
        Fund(
            FundId(id), name, ConsumptionMode.DETAILED, null, LocalDate.of(2026, 10, 1), null, pen(allocated), pen(0),
            status, null, null, null,
        ),
        pen(detailed), 0, null, pen(allocated - detailed), null, pen(allocated - detailed),
    )
    FundsUiState(
        loaded = true,
        funds = listOf(overview("fare", "Pasajes", 5_000, 1_200), overview("old", "Viejo", 1_000, 1_000, FundStatus.CLOSED)),
        summary = FundSummary(pen(3_800), pen(96_200)),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FundsContentPreview() {
    CompositionLocalProvider(LocalHideAmounts provides false) { FundsContent(previewState, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun FundsEmptyPreview() {
    FundsContent(FundsUiState(loaded = true), {}, {})
}
