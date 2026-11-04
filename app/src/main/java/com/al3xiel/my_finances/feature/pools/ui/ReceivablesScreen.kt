package com.al3xiel.my_finances.feature.pools.ui

import com.al3xiel.my_finances.feature.loans.ui.components.loanStatusLabelRes
import com.al3xiel.my_finances.feature.loans.ui.components.badge
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.AppIcons
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.pools.domain.model.LoanReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PersonReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PoolReceivable
import com.al3xiel.my_finances.feature.pools.ui.components.ReceivableRow
import com.al3xiel.my_finances.feature.pools.ui.components.ReceivableStatus
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** S30: lo que te deben, por persona, por cúmulo y por deuda suelta. */
@Composable
fun ReceivablesScreen(
    onOpenPerson: (PersonId) -> Unit,
    onOpenPool: (PoolId) -> Unit,
    onOpenLoan: (LoanId) -> Unit,
    onNewPool: () -> Unit,
    onNewLoan: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceivablesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReceivablesContent(state, onOpenPerson, onOpenPool, onOpenLoan, onNewPool, onNewLoan, modifier)
}

@Composable
fun ReceivablesContent(
    state: ReceivablesUiState,
    onOpenPerson: (PersonId) -> Unit,
    onOpenPool: (PoolId) -> Unit,
    onOpenLoan: (LoanId) -> Unit,
    onNewPool: () -> Unit,
    onNewLoan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    ScreenScaffold(title = stringResource(R.string.tab_receivables), showBack = false, modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item { ReceivablesHeader(state, onNewPool, onNewLoan) }
            if (state.isEmpty) {
                item {
                    EmptyState(
                        title = stringResource(R.string.receivables_empty_title),
                        body = stringResource(R.string.receivables_empty_body),
                    )
                }
            } else {
                receivablesSections(state, onOpenPerson, onOpenPool, onOpenLoan)
            }
        }
    }
}

@Composable
private fun ReceivablesHeader(state: ReceivablesUiState, onNewPool: () -> Unit, onNewLoan: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Column(modifier = Modifier.padding(horizontal = spacing.sm)) {
            Text(
                stringResource(R.string.receivables_total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(state.total, style = MaterialTheme.typography.displaySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
            PillButton(stringResource(R.string.receivables_new_pool), onNewPool, Modifier.weight(1f), PillStyle.TONAL, icon = AppIcons.Add)
            PillButton(stringResource(R.string.receivables_new_loan), onNewLoan, Modifier.weight(1f), PillStyle.TONAL, icon = AppIcons.Add)
        }
    }
}

private fun LazyListScope.receivablesSections(
    state: ReceivablesUiState,
    onOpenPerson: (PersonId) -> Unit,
    onOpenPool: (PoolId) -> Unit,
    onOpenLoan: (LoanId) -> Unit,
) {
    if (state.people.isNotEmpty()) {
        item {
            SectionCard(title = stringResource(R.string.receivables_by_person)) {
                state.people.forEachIndexed { index, person ->
                    if (index > 0) SectionDivider()
                    ReceivableRow(title = person.alias, subtitle = null, amount = person.total, onClick = { onOpenPerson(person.personId) })
                }
            }
        }
    }
    if (state.pools.isNotEmpty()) {
        item {
            SectionCard(title = stringResource(R.string.receivables_pools)) {
                state.pools.forEachIndexed { index, pool ->
                    if (index > 0) SectionDivider()
                    ReceivableRow(
                        title = pool.name,
                        subtitle = stringResource(R.string.receivables_pool_subtitle, pool.responsibleAlias),
                        amount = pool.totalPending,
                        detailLabel = stringResource(R.string.receivables_pool_period),
                        detailAmount = pool.periodPending,
                        onClick = { onOpenPool(pool.poolId) },
                        status = when {
                            pool.periodPending.isPositive -> ReceivableStatus(BadgeStatus.PENDING)
                            pool.totalPending.isPositive -> null
                            else -> ReceivableStatus(BadgeStatus.RECONCILED, R.string.receivables_pool_clear)
                        },
                    )
                }
            }
        }
    }
    if (state.loans.isNotEmpty()) {
        item {
            SectionCard(title = stringResource(R.string.receivables_loans)) {
                state.loans.forEachIndexed { index, loan ->
                    if (index > 0) SectionDivider()
                                        ReceivableRow(
                        title = loan.alias,
                        subtitle = null,
                        subtitleDay = loan.occurredOn,
                        amount = loan.pending,
                        onClick = { onOpenLoan(loan.loanId) },
                        status = ReceivableStatus(LoanStatus.OPEN.badge(), loanStatusLabelRes(LoanStatus.OPEN, loan.isPartlyRepaid)),
                    )
                }
            }
        }
    }
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private val previewState = ReceivablesUiState(
    total = pen(14_000),
    people = listOf(
        PersonReceivable(PersonId("mom"), "Mamá", pen(11_000), pen(0)),
        PersonReceivable(PersonId("pepe"), "Pepe", pen(0), pen(3_000)),
    ),
    pools = listOf(PoolReceivable(PoolId("dogs"), "Perros – mamá", PoolKind.PERMANENT, PersonId("mom"), "Mamá", pen(6_000), pen(11_000))),
    loans = listOf(LoanReceivable(LoanId("l1"), PersonId("pepe"), "Pepe", pen(5_000), pen(2_000), LocalDate.of(2026, 11, 4))),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ReceivablesContentPreview() {
    MyfinancesTheme { ReceivablesContent(previewState, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun ReceivablesEmptyPreview() {
    MyfinancesTheme { ReceivablesContent(ReceivablesUiState(), {}, {}, {}, {}, {}) }
}
