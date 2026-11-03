package com.al3xiel.my_finances.feature.loans.ui

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.NegativeBalanceDialog
import com.al3xiel.my_finances.core.designsystem.NoticeDialog
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.ReasonDialog
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.core.designsystem.StatusBadge
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.loans.domain.model.Loan
import com.al3xiel.my_finances.feature.loans.domain.model.LoanDetail
import com.al3xiel.my_finances.feature.loans.domain.model.LoanRepaymentLine
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import com.al3xiel.my_finances.feature.loans.ui.components.LoanPayDialogContent
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** S36: la deuda con sus pagos; registrar un pago y anular el préstamo o un pago (D20). */
@Composable
fun LoanDetailScreen(
    loanId: LoanId,
    modifier: Modifier = Modifier,
    viewModel: LoanDetailViewModel = hiltViewModel<LoanDetailViewModel, LoanDetailViewModel.Factory>(
        key = loanId.value,
        creationCallback = { it.create(loanId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoanDetailContent(state, viewModel::onAction, modifier)
}

@Composable
fun LoanDetailContent(
    state: LoanDetailUiState,
    onAction: (LoanDetailUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    if (detail == null) {
        ScreenScaffold(title = "", modifier = modifier) { padding ->
            if (state.notFound) EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = Modifier.padding(padding))
        }
        return
    }
    val loan = detail.loan
    val spacing = LocalSpacing.current
    ScreenScaffold(title = stringResource(R.string.loan_detail_title, detail.personName), modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item {
                SectionCard {
                    Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            Text(dayText(loan.occurredOn), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            StatusBadge(loan.status.badge(loan.repaid.isPositive), label = stringResource(loan.status.labelRes(loan.repaid.isPositive)))
                        }
                        // Decorativa: lo pagado y lo pendiente van en texto debajo.
                        val fraction = if (loan.principal.amountMinor <= 0) 0f else (loan.repaid.amountMinor.toFloat() / loan.principal.amountMinor).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
                        AmountLine(stringResource(R.string.loan_detail_principal), loan.principal)
                        AmountLine(stringResource(R.string.loan_detail_repaid), loan.repaid)
                        AmountLine(stringResource(R.string.loan_detail_pending), loan.pending, emphasis = true)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    if (loan.pending.isPositive) {
                        PillButton(stringResource(R.string.loan_detail_register_payment), { onAction(LoanDetailUiAction.OpenPay) }, Modifier.fillMaxWidth())
                    }
                    PillButton(
                        stringResource(R.string.loan_detail_void_loan),
                        { onAction(LoanDetailUiAction.AskVoidLoan) },
                        Modifier.align(Alignment.End),
                        PillStyle.TEXT,
                    )
                }
            }
            item {
                SectionCard(title = stringResource(R.string.loan_detail_payments)) {
                    if (detail.repayments.isEmpty()) {
                        SectionRow(headline = stringResource(R.string.loan_detail_no_payments))
                    }
                    detail.repayments.forEachIndexed { index, payment ->
                        if (index > 0) SectionDivider()
                        SectionRow(
                            headline = dayText(payment.occurredOn),
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MoneyText(payment.amount)
                                    PillButton(
                                        stringResource(R.string.loan_detail_void_payment),
                                        { onAction(LoanDetailUiAction.AskVoidRepayment(payment.movementId)) },
                                        style = PillStyle.TEXT,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    Dialogs(state, onAction)
}

@Composable
private fun Dialogs(state: LoanDetailUiState, onAction: (LoanDetailUiAction) -> Unit) {
    state.pay?.let { dialog ->
        LoanPayDialogContent(
            dialog = dialog,
            accountOptions = state.accountOptions,
            onAmountChange = { onAction(LoanDetailUiAction.EditPayAmount(it)) },
            onAccountSelect = { onAction(LoanDetailUiAction.SelectPayAccount(it)) },
            onConfirm = { onAction(LoanDetailUiAction.ConfirmPay) },
            onDismiss = { onAction(LoanDetailUiAction.DismissPay) },
        )
    }
    if (state.voiding != null && state.negativeWarning == null) {
        ReasonDialog(
            title = stringResource(R.string.loan_detail_void_title),
            onConfirm = { onAction(LoanDetailUiAction.ConfirmVoid(it)) },
            onDismiss = { onAction(LoanDetailUiAction.DismissVoid) },
        )
    }
    state.negativeWarning?.let {
        NegativeBalanceDialog(
            warning = it,
            onConfirm = { onAction(LoanDetailUiAction.ConfirmNegativeBalance) },
            onDismiss = { onAction(LoanDetailUiAction.DismissWarning) },
        )
    }
    state.notice?.let { NoticeDialog(error = it, onDismiss = { onAction(LoanDetailUiAction.DismissNotice) }) }
}

@Composable
private fun AmountLine(label: String, amount: Money, emphasis: Boolean = false) {
    val style = if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        MoneyText(amount, style = style)
    }
}

/** Tabla de estados (§7): abierta y parcial esperan; pagada ya está. */
private fun LoanStatus.badge(partlyRepaid: Boolean) = when (this) {
    LoanStatus.OPEN -> BadgeStatus.PENDING
    LoanStatus.REPAID -> BadgeStatus.RECONCILED
}

private fun LoanStatus.labelRes(partlyRepaid: Boolean) = when (this) {
    LoanStatus.OPEN -> if (partlyRepaid) R.string.status_partial else R.string.loan_detail_status_open
    LoanStatus.REPAID -> R.string.loan_detail_status_repaid
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private val previewState = LoanDetailUiState(
    loaded = true,
    detail = LoanDetail(
        Loan(LoanId("l1"), PersonId("pepe"), MovementId("ml1"), pen(5_000), pen(2_000), LoanStatus.OPEN, LocalDate.of(2026, 11, 4)),
        "Pepe",
        listOf(LoanRepaymentLine(MovementId("mr1"), pen(2_000), LocalDate.of(2026, 11, 5))),
    ),
    accountOptions = listOf(EntityOption("bbva", "BBVA")),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun LoanDetailContentPreview() {
    MyfinancesTheme { LoanDetailContent(previewState, {}) }
}
