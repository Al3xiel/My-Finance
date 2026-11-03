package com.al3xiel.my_finances.feature.people.ui

import com.al3xiel.my_finances.feature.loans.ui.components.loanStatusLabelRes
import com.al3xiel.my_finances.feature.loans.ui.components.badge
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
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
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.model.PersonDetail
import com.al3xiel.my_finances.feature.people.domain.model.PersonLoanLine
import com.al3xiel.my_finances.feature.people.domain.model.PersonPartLine
import com.al3xiel.my_finances.feature.people.domain.model.ReimbursementLine
import com.al3xiel.my_finances.feature.pools.domain.model.LoanReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PoolReceivable
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** S35: saldo con la persona, sus cúmulos, deudas, reembolsos y los gastos con su concepto. */
@Composable
fun PersonDetailScreen(
    personId: PersonId,
    onOpenPool: (PoolId) -> Unit,
    onOpenLoan: (LoanId) -> Unit,
    onReceiveReimbursement: (PersonId) -> Unit,
    onLend: (PersonId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonDetailViewModel = hiltViewModel<PersonDetailViewModel, PersonDetailViewModel.Factory>(
        key = personId.value,
        creationCallback = { it.create(personId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PersonDetailContent(state, onOpenPool, onOpenLoan, onReceiveReimbursement, onLend, modifier)
}

@Composable
fun PersonDetailContent(
    state: PersonDetailUiState,
    onOpenPool: (PoolId) -> Unit,
    onOpenLoan: (LoanId) -> Unit,
    onReceiveReimbursement: (PersonId) -> Unit,
    onLend: (PersonId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    if (detail == null) {
        ScreenScaffold(title = "", modifier = modifier) { padding ->
            if (state.notFound) EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = Modifier.padding(padding))
        }
        return
    }
    val spacing = LocalSpacing.current
    ScreenScaffold(title = detail.person.alias, modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Column(modifier = Modifier.padding(horizontal = spacing.sm)) {
                        Text(
                            stringResource(R.string.person_detail_balance),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MoneyText(detail.balance, style = MaterialTheme.typography.displaySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        PillButton(stringResource(R.string.person_detail_receive), { onReceiveReimbursement(detail.person.id) }, Modifier.weight(1f))
                        PillButton(stringResource(R.string.person_detail_lend), { onLend(detail.person.id) }, Modifier.weight(1f), PillStyle.TONAL)
                    }
                }
            }
            if (detail.pools.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.person_detail_pools)) {
                        detail.pools.forEachIndexed { index, pool ->
                            if (index > 0) SectionDivider()
                            Line(pool.name, null, pool.totalPending, onClick = { onOpenPool(pool.poolId) })
                        }
                    }
                }
            }
            if (detail.loans.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.person_detail_loans)) {
                        detail.loans.forEachIndexed { index, line ->
                            if (index > 0) SectionDivider()
                            Line(
                                dayText(line.loan.occurredOn),
                                null,
                                line.loan.pending,
                                onClick = { onOpenLoan(line.loan.loanId) },
                                badge = line.status.badge() to stringResource(loanStatusLabelRes(line.status, line.loan.isPartlyRepaid)),
                            )
                        }
                    }
                }
            }
            if (detail.reimbursements.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.person_detail_reimbursements)) {
                        detail.reimbursements.forEachIndexed { index, line ->
                            if (index > 0) SectionDivider()
                            Line(
                                dayText(line.occurredOn),
                                if (line.isVoided) stringResource(R.string.person_detail_voided) else null,
                                line.amount,
                                strike = line.isVoided,
                            )
                        }
                    }
                }
            }
            if (detail.parts.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.person_detail_expenses)) {
                        detail.parts.forEachIndexed { index, part ->
                            if (index > 0) SectionDivider()
                            Line(
                                (listOf(part.categoryName) + part.tagNames).joinToString(" · "),
                                "${part.poolName} · ${dayText(part.occurredOn)}",
                                part.pending,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(
    title: String,
    subtitle: String?,
    amount: Money,
    onClick: (() -> Unit)? = null,
    strike: Boolean = false,
    badge: Pair<BadgeStatus, String>? = null,
) {
    SectionRow(
        headline = title,
        supporting = subtitle,
        onClick = onClick,
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MoneyText(amount, style = MaterialTheme.typography.bodyLarge, textDecoration = if (strike) TextDecoration.LineThrough else null)
                badge?.let { (status, label) -> StatusBadge(status, label = label) }
            }
        },
    )
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private val previewState = run {
    val mom = PersonId("mom")
    PersonDetailUiState(
        loaded = true,
        detail = PersonDetail(
            person = Person(mom, "Mamá", false),
            balance = pen(11_000),
            pools = listOf(PoolReceivable(PoolId("dogs"), "Perros – mamá", PoolKind.PERMANENT, mom, "Mamá", pen(6_000), pen(11_000))),
            loans = listOf(PersonLoanLine(LoanReceivable(LoanId("l1"), mom, "Mamá", pen(5_000), pen(2_000), LocalDate.of(2026, 11, 4)), LoanStatus.OPEN)),
            reimbursements = listOf(
                ReimbursementLine(MovementId("mr1"), pen(15_000), LocalDate.of(2026, 10, 20), false),
                ReimbursementLine(MovementId("mr2"), pen(4_000), LocalDate.of(2026, 10, 25), true),
            ),
            parts = listOf(PersonPartLine(PartId("p4"), "Perros – mamá", LocalDate.of(2026, 11, 2), "Mascotas", listOf("Perros"), pen(6_000), pen(6_000))),
        ),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun PersonDetailContentPreview() {
    MyfinancesTheme { PersonDetailContent(previewState, {}, {}, {}, {}) }
}
