package com.al3xiel.my_finances.feature.funds.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.DateField
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.NoticeDialog
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.feature.funds.domain.model.Fund
import com.al3xiel.my_finances.feature.funds.domain.model.FundCountRecord
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.ui.components.CloseFundDialog
import com.al3xiel.my_finances.feature.funds.ui.components.CountRow
import com.al3xiel.my_finances.feature.funds.ui.components.FundActions
import com.al3xiel.my_finances.feature.funds.ui.components.FundHeader
import com.al3xiel.my_finances.feature.funds.ui.components.TopUpDialog
import com.al3xiel.my_finances.feature.funds.ui.components.UsageDayRow
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** S52 (detalle): cifras del fondo, días de uso, arqueos y acciones. */
@Composable
fun FundDetailScreen(
    fundId: FundId,
    onCount: (FundId) -> Unit,
    onEdit: (FundId) -> Unit,
    onAddExpense: (FundId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FundDetailViewModel = hiltViewModel<FundDetailViewModel, FundDetailViewModel.Factory>(
        key = fundId.value,
        creationCallback = { it.create(fundId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FundDetailContent(state, viewModel::onAction, onCount, onEdit, onAddExpense, modifier)
}

private enum class FundDialog { TOP_UP, CLOSE }

@Composable
fun FundDetailContent(
    state: FundDetailUiState,
    onAction: (FundDetailUiAction) -> Unit,
    onCount: (FundId) -> Unit,
    onEdit: (FundId) -> Unit,
    onAddExpense: (FundId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overview = state.overview
    if (overview == null) {
        ScreenScaffold(title = "", modifier = modifier) { padding ->
            if (state.notFound) EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = Modifier.padding(padding))
        }
        return
    }
    val fund = overview.fund
    var dialog by rememberSaveable { mutableStateOf<FundDialog?>(null) }
    var day by rememberSaveable { mutableStateOf(state.today) }
    val spacing = LocalSpacing.current
    ScreenScaffold(title = fund.name, modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item { FundHeader(overview) }
            if (fund.status == FundStatus.ACTIVE) {
                item { FundActions(fund.id, onCount, onEdit, onAddExpense, onTopUp = { dialog = FundDialog.TOP_UP }, onClose = { dialog = FundDialog.CLOSE }) }
                if (fund.mode == ConsumptionMode.DAILY_ESTIMATE) {
                    item {
                        UsageDayRow(day, state.today, marked = day in state.usageDays, onDay = { day = it }, onToggle = { onAction(FundDetailUiAction.ToggleDay(day)) })
                    }
                }
            }
            if (state.counts.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.fund_counts_history)) {
                        state.counts.forEachIndexed { index, count ->
                            if (index > 0) SectionDivider()
                            CountRow(count)
                        }
                    }
                }
            }
        }
    }
    when (dialog) {
        FundDialog.TOP_UP -> TopUpDialog(
            onConfirm = { onAction(FundDetailUiAction.TopUp(it)); dialog = null },
            onDismiss = { dialog = null },
        )
        FundDialog.CLOSE -> CloseFundDialog(
            targets = state.rollOverTargets.map { EntityOption(it.fund.id.value, it.fund.name) },
            onConfirm = { policy, target -> onAction(FundDetailUiAction.Close(policy, target)); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
    state.notice?.let { NoticeDialog(error = it, onDismiss = { onAction(FundDetailUiAction.DismissNotice) }) }
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private val previewState = run {
    val fund = Fund(
        FundId("fare"), "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
        pen(5_000), pen(300), FundStatus.ACTIVE, null, null, null,
    )
    FundDetailUiState(
        loaded = true,
        overview = FundOverview(fund, pen(1_200), 3, LocalDate.of(2026, 10, 20), pen(3_500), pen(1_500), pen(3_500)),
        today = LocalDate.of(2026, 10, 27),
        counts = listOf(FundCountRecord(pen(3_800), pen(1_200), pen(1_000), pen(200), LocalDate.of(2026, 10, 20))),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun FundDetailContentPreview() {
    MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { FundDetailContent(previewState, {}, {}, {}, {}) } }
}
