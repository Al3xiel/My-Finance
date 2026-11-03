package com.al3xiel.my_finances.feature.accounts.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.StatusBadge
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.accounts.ui.AccountDetailUiState
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.ui.components.labelRes
import java.time.Instant
import java.time.LocalDate

/** Botón "Conciliar" (o "Continuar" si hay una abierta) y la última conciliación con su estado y su aviso de revisión. */
@Composable
internal fun ReconciliationEntry(
    state: AccountDetailUiState,
    onReconcile: () -> Unit,
    onOpenReconciliation: (ReconciliationId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(
            text = stringResource(
                if (state.hasOpenReconciliation) R.string.account_detail_reconcile_continue else R.string.account_detail_reconcile,
            ),
            onClick = onReconcile,
            style = PillStyle.TONAL,
            modifier = Modifier.fillMaxWidth(),
        )
        val latest = state.latestReconciliation ?: return@Column
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpenReconciliation(latest.id) },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(stringResource(R.string.account_detail_last_reconciliation), style = MaterialTheme.typography.labelMedium)
            val date = state.latestReconciledOn?.let { dayText(it) }.orEmpty()
            Text(stringResource(R.string.account_detail_last_line, stringResource(latest.status.labelRes()), date), style = MaterialTheme.typography.bodyMedium)
            if (latest.needsReview) {
                StatusBadge(BadgeStatus.NEEDS_REVIEW)
                Text(stringResource(R.string.reconciliation_needs_review), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReconciliationEntryPreview() {
    val latest = Reconciliation(
        ReconciliationId("r1"), AccountId("bcp"), Money(81_240, CurrencyCode.PEN), Money(80_000, CurrencyCode.PEN),
        ReconciliationStatus.RESOLVED, null, null, null, Instant.EPOCH, null, needsReview = true,
    )
    ReconciliationEntry(
        AccountDetailUiState(canReconcile = true, latestReconciliation = latest, latestReconciledOn = LocalDate.of(2026, 10, 25)),
        {},
        {},
    )
}
