package com.al3xiel.my_finances.feature.people.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.MoneyText
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
        if (state.notFound) EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(detail.person.alias, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.person_detail_balance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyText(detail.balance, style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Button(onClick = { onReceiveReimbursement(detail.person.id) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.person_detail_receive))
                    }
                    OutlinedButton(onClick = { onLend(detail.person.id) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.person_detail_lend))
                    }
                }
            }
        }
        if (detail.pools.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.person_detail_pools)) }
            items(detail.pools, key = { "pool-${it.poolId.value}" }) { pool ->
                Line(pool.name, null, pool.totalPending, onClick = { onOpenPool(pool.poolId) })
            }
        }
        if (detail.loans.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.person_detail_loans)) }
            items(detail.loans, key = { "loan-${it.loan.loanId.value}" }) { line ->
                val repaid = line.status == LoanStatus.REPAID
                Line(
                    dayText(line.loan.occurredOn),
                    if (repaid) stringResource(R.string.person_detail_loan_repaid) else null,
                    line.loan.pending,
                    onClick = { onOpenLoan(line.loan.loanId) },
                )
            }
        }
        if (detail.reimbursements.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.person_detail_reimbursements)) }
            items(detail.reimbursements, key = { "reimbursement-${it.movementId.value}" }) { line ->
                Line(
                    dayText(line.occurredOn),
                    if (line.isVoided) stringResource(R.string.person_detail_voided) else null,
                    line.amount,
                    strike = line.isVoided,
                )
            }
        }
        if (detail.parts.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.person_detail_expenses)) }
            items(detail.parts, key = { "part-${it.partId.value}" }) { part ->
                Line(
                    (listOf(part.categoryName) + part.tagNames).joinToString(" · "),
                    "${part.poolName} · ${dayText(part.occurredOn)}",
                    part.pending,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Line(title: String, subtitle: String?, amount: Money, onClick: (() -> Unit)? = null, strike: Boolean = false) {
    val base = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    Row(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, textDecoration = if (strike) TextDecoration.LineThrough else null)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        MoneyText(amount, style = MaterialTheme.typography.bodyLarge, textDecoration = if (strike) TextDecoration.LineThrough else null)
    }
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
@Composable
private fun PersonDetailContentPreview() {
    PersonDetailContent(previewState, {}, {}, {}, {})
}
