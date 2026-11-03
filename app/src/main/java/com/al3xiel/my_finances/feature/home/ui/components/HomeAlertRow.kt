package com.al3xiel.my_finances.feature.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.home.domain.model.HomeAlert

/** Una alerta derivada de Inicio: no se descarta, desaparece sola al resolverse su causa (D58). */
@Composable
fun HomeAlertRow(alert: HomeAlert, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = stringResource(alert.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun HomeAlert.messageRes(): Int = when (this) {
    is HomeAlert.NeedsReview -> R.string.home_alert_needs_review
    is HomeAlert.OpenReconciliation -> R.string.home_alert_open_reconciliation
    is HomeAlert.OverdueIncome -> R.string.home_alert_overdue_income
    HomeAlert.BackupOverdue -> R.string.home_alert_backup_overdue
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeAlertRowPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeAlertRow(HomeAlert.NeedsReview(ReconciliationId("r")), onClick = {})
        HomeAlertRow(HomeAlert.OverdueIncome(ExpectedIncomeId("e")), onClick = {})
        HomeAlertRow(HomeAlert.BackupOverdue, onClick = {})
    }
}
