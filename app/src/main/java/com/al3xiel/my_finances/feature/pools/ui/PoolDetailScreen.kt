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
import androidx.compose.material3.Card
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
import com.al3xiel.my_finances.core.designsystem.AppIcons
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.NoticeDialog
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.model.PoolDetail
import com.al3xiel.my_finances.feature.pools.domain.model.PoolPeriod
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
import com.al3xiel.my_finances.feature.pools.ui.components.PeriodCard
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** S31: periodo actual, historial y acciones de un cúmulo. */
@Composable
fun PoolDetailScreen(
    poolId: PoolId,
    onAddExpense: (PoolId) -> Unit,
    onSettle: (PoolId) -> Unit,
    onReceiveReimbursement: (PersonId, PoolId) -> Unit,
    onEdit: (PoolId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PoolDetailViewModel = hiltViewModel<PoolDetailViewModel, PoolDetailViewModel.Factory>(
        key = poolId.value,
        creationCallback = { it.create(poolId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PoolDetailContent(state, viewModel::onAction, onAddExpense, onSettle, onReceiveReimbursement, onEdit, modifier)
}

@Composable
fun PoolDetailContent(
    state: PoolDetailUiState,
    onAction: (PoolDetailUiAction) -> Unit,
    onAddExpense: (PoolId) -> Unit,
    onSettle: (PoolId) -> Unit,
    onReceiveReimbursement: (PersonId, PoolId) -> Unit,
    onEdit: (PoolId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    if (detail == null) {
        ScreenScaffold(title = "", modifier = modifier) { padding ->
            if (state.notFound) EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = Modifier.padding(padding))
        }
        return
    }
    val pool = detail.pool
    val spacing = LocalSpacing.current
    ScreenScaffold(title = pool.name, modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = spacing.sm)) {
                    Text(
                        stringResource(R.string.pool_responsible_kind, detail.responsibleAlias, stringResource(pool.kind.labelRes())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.pool_pending_total),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                    MoneyText(detail.pendingTotal, style = MaterialTheme.typography.displaySmall)
                }
            }
            detail.currentPeriod?.let { period ->
                item { PeriodCard(period, title = stringResource(R.string.pool_current_period)) }
            }
            if (!pool.isArchived) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        PillButton(stringResource(R.string.pool_add_expense), { onAddExpense(pool.id) }, Modifier.fillMaxWidth(), icon = AppIcons.Add)
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
                            PillButton(stringResource(R.string.pool_settle), { onSettle(pool.id) }, Modifier.weight(1f), PillStyle.TONAL)
                            PillButton(
                                stringResource(R.string.pool_receive_reimbursement),
                                { onReceiveReimbursement(pool.responsiblePersonId, pool.id) },
                                Modifier.weight(1f),
                                PillStyle.TONAL,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            PillButton(stringResource(R.string.pool_edit), { onEdit(pool.id) }, style = PillStyle.TEXT)
                            PillButton(stringResource(R.string.pool_archive), { onAction(PoolDetailUiAction.Archive) }, style = PillStyle.TEXT)
                        }
                    }
                }
            }
            val history = detail.periods.filter { it.closedOn != null }
            if (history.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.pool_history),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = spacing.lg),
                    )
                }
                items(history, key = { it.id.value }) { period -> PeriodCard(period, title = null) }
            }
        }
    }
    state.notice?.let { NoticeDialog(error = it, onDismiss = { onAction(PoolDetailUiAction.DismissNotice) }) }
}

internal fun PoolKind.labelRes() = when (this) {
    PoolKind.PERMANENT -> R.string.pool_kind_permanent
    PoolKind.EVENT -> R.string.pool_kind_event
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private val previewState = run {
    val id = PoolId("dogs")
    val pool = Pool(id, "Perros – mamá", PersonId("mom"), PoolKind.PERMANENT, null, emptyList(), false)
    PoolDetailUiState(
        loaded = true,
        detail = PoolDetail(
            pool, "Mamá",
            listOf(
                PoolPeriod(PoolPeriodId("dogs-p2"), id, LocalDate.of(2026, 10, 31), null, PoolPeriodStatus.OPEN, null, pen(6_000), pen(0)),
                PoolPeriod(PoolPeriodId("dogs-p1"), id, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), PoolPeriodStatus.STATEMENT_SENT, null, pen(20_000), pen(15_000)),
            ),
            pen(11_000),
        ),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun PoolDetailContentPreview() {
    MyfinancesTheme { PoolDetailContent(previewState, {}, {}, {}, { _, _ -> }, {}) }
}
