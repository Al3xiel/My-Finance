package com.al3xiel.my_finances.feature.reconciliation.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.ReasonDialog
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.StatusBadge
import com.al3xiel.my_finances.core.designsystem.message
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationDetail
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationMovement
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.ui.components.DifferenceText
import com.al3xiel.my_finances.feature.reconciliation.ui.components.LabeledMoney
import com.al3xiel.my_finances.feature.reconciliation.ui.components.MovementSection
import com.al3xiel.my_finances.feature.reconciliation.ui.components.RegisterForgottenButton
import com.al3xiel.my_finances.feature.reconciliation.ui.components.ResolveButton
import com.al3xiel.my_finances.feature.reconciliation.ui.components.badge
import com.al3xiel.my_finances.feature.reconciliation.ui.components.isCorrectable
import com.al3xiel.my_finances.feature.reconciliation.ui.components.labelRes
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.Instant
import java.time.LocalDate

/**
 * S61: conciliación abierta. Registrar un olvidado y corregir un vinculado abren los editores ya ligados a ella
 * (`onRegisterForgotten`, `onCorrect`); resolver lleva a S62. `onFinished` cierra la pantalla al cancelar.
 */
@Composable
fun ReconciliationDetailScreen(
    reconciliationId: ReconciliationId,
    onRegisterForgotten: (MovementType) -> Unit,
    onCorrect: (ReconciliationMovement) -> Unit,
    onResolve: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onExportDiagnostic: () -> Unit = {},
    onExportDictionary: () -> Unit = {},
    viewModel: ReconciliationDetailViewModel = hiltViewModel<ReconciliationDetailViewModel, ReconciliationDetailViewModel.Factory>(
        key = reconciliationId.value,
        creationCallback = { it.create(reconciliationId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onFinished() }
    ReconciliationDetailContent(state, viewModel::onAction, onRegisterForgotten, onCorrect, onResolve, modifier, onExportDiagnostic, onExportDictionary)
}

@Composable
fun ReconciliationDetailContent(
    state: ReconciliationDetailUiState,
    onAction: (ReconciliationDetailUiAction) -> Unit,
    onRegisterForgotten: (MovementType) -> Unit,
    onCorrect: (ReconciliationMovement) -> Unit,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier,
    onExportDiagnostic: () -> Unit = {},
    onExportDictionary: () -> Unit = {},
) {
    val detail = state.detail
    if (detail == null) {
        ScreenScaffold(title = "", modifier = modifier) { padding ->
            if (state.notFound) EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = Modifier.padding(padding))
        }
        return
    }
    val spacing = LocalSpacing.current
    ScreenScaffold(title = stringResource(R.string.reconciliation_detail_title, state.accountName.orEmpty()), modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            SectionCard {
                Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(detail.reconciliation.status.badge(), label = stringResource(detail.reconciliation.status.labelRes()))
                        if (detail.reconciliation.needsReview) StatusBadge(BadgeStatus.NEEDS_REVIEW)
                    }
                    if (detail.reconciliation.needsReview) {
                        Text(stringResource(R.string.reconciliation_needs_review), style = MaterialTheme.typography.bodyMedium)
                    }
                    LabeledMoney(stringResource(R.string.reconciliation_real), detail.reconciliation.real)
                    LabeledMoney(stringResource(R.string.reconciliation_app_at_check), detail.computed)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reconciliation_remaining), style = MaterialTheme.typography.titleMedium)
                        DifferenceText(detail.difference, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            MovementSection(
                title = stringResource(R.string.reconciliation_linked_title),
                movements = detail.linked,
                emptyText = stringResource(R.string.reconciliation_linked_empty),
                actionLabel = stringResource(R.string.reconciliation_correct).takeIf { state.canAct },
                onAction = { if (it.isCorrectable()) onCorrect(it) },
            )
            if (state.canAct) {
                MovementSection(
                    title = stringResource(R.string.reconciliation_suggestions_title),
                    hint = stringResource(R.string.reconciliation_suggestions_hint),
                    movements = detail.suggestions,
                    emptyText = "",
                    actionLabel = stringResource(R.string.reconciliation_link),
                    onAction = { onAction(ReconciliationDetailUiAction.Link(it.id)) },
                )
                RegisterForgottenButton(onRegisterForgotten)
                ResolveButton(onResolve)
                PillButton(
                    stringResource(R.string.reconciliation_cancel),
                    { onAction(ReconciliationDetailUiAction.AskCancel) },
                    Modifier.fillMaxWidth(),
                    PillStyle.TEXT,
                )
            }
            PillButton(stringResource(R.string.reconciliation_export_diagnostic), onExportDiagnostic, Modifier.fillMaxWidth(), PillStyle.TEXT)
            PillButton(stringResource(R.string.reconciliation_export_dictionary), onExportDictionary, Modifier.fillMaxWidth(), PillStyle.TEXT)
            state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (state.askingCancel) {
        ReasonDialog(
            title = stringResource(R.string.reconciliation_cancel_title),
            onConfirm = { onAction(ReconciliationDetailUiAction.ConfirmCancel(it)) },
            onDismiss = { onAction(ReconciliationDetailUiAction.DismissCancel) },
        )
    }
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private fun movement(id: String, type: MovementType, minor: Long, note: String?) =
    ReconciliationMovement(MovementId(id), type, AccountId("bcp"), null, pen(minor), LocalDate.of(2026, 10, 25), note, MovementStatus.ACTIVE)

private val previewReconciliation = Reconciliation(
    ReconciliationId("r1"), AccountId("bcp"), pen(81_240), pen(80_000), ReconciliationStatus.OPEN, null, null, null,
    Instant.EPOCH, null, false,
)

private val previewDetail = ReconciliationDetail(
    previewReconciliation,
    computed = pen(81_000),
    difference = pen(240),
    linked = listOf(movement("m1", MovementType.INCOME, 1_000, "Reembolso olvidado")),
    suggestions = listOf(movement("m2", MovementType.EXPENSE, 300, "Café")),
)

private val previewState = ReconciliationDetailUiState(loaded = true, accountName = "BCP", canAct = true, detail = previewDetail)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ReconciliationDetailContentPreview() {
    MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { ReconciliationDetailContent(previewState, {}, {}, {}, {}) } }
}

@Preview(showBackground = true)
@Composable
private fun ReconciliationDetailResolvedPreview() {
    val resolved = previewDetail.copy(reconciliation = previewReconciliation.copy(status = ReconciliationStatus.RESOLVED, needsReview = true))
    MyfinancesTheme { ReconciliationDetailContent(previewState.copy(canAct = false, detail = resolved), {}, {}, {}, {}) }
}
