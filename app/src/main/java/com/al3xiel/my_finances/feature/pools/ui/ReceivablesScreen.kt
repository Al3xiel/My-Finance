package com.al3xiel.my_finances.feature.pools.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.MoneyText
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
    Column(modifier = modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedButton(onClick = onNewPool, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.receivables_new_pool)) }
            OutlinedButton(onClick = onNewLoan, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.receivables_new_loan)) }
        }
        ReceivablesList(state, onOpenPerson, onOpenPool, onOpenLoan, Modifier.weight(1f))
    }
}

@Composable
private fun ReceivablesList(
    state: ReceivablesUiState,
    onOpenPerson: (PersonId) -> Unit,
    onOpenPool: (PoolId) -> Unit,
    onOpenLoan: (LoanId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isEmpty) {
        EmptyState(
            title = stringResource(R.string.receivables_empty_title),
            body = stringResource(R.string.receivables_empty_body),
            modifier = modifier,
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    stringResource(R.string.receivables_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyText(state.total, style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (state.people.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.receivables_by_person)) }
            items(state.people, key = { "person-${it.personId.value}" }) { person ->
                ReceivableRow(
                    title = person.alias,
                    subtitle = null,
                    amount = person.total,
                    onClick = { onOpenPerson(person.personId) },
                )
            }
        }
        if (state.pools.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.receivables_pools)) }
            items(state.pools, key = { "pool-${it.poolId.value}" }) { pool ->
                ReceivableRow(
                    title = pool.name,
                    subtitle = stringResource(R.string.receivables_pool_subtitle, pool.responsibleAlias),
                    amount = pool.totalPending,
                    detailLabel = stringResource(R.string.receivables_pool_period),
                    detailAmount = pool.periodPending,
                    onClick = { onOpenPool(pool.poolId) },
                )
            }
        }
        if (state.loans.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.receivables_loans)) }
            items(state.loans, key = { "loan-${it.loanId.value}" }) { loan ->
                ReceivableRow(
                    title = loan.alias,
                    subtitle = null,
                    subtitleDay = loan.occurredOn,
                    amount = loan.pending,
                    onClick = { onOpenLoan(loan.loanId) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
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
@Composable
private fun ReceivablesContentPreview() {
    ReceivablesContent(previewState, {}, {}, {}, {}, {})
}

@Preview(showBackground = true)
@Composable
private fun ReceivablesEmptyPreview() {
    ReceivablesContent(ReceivablesUiState(), {}, {}, {}, {}, {})
}
