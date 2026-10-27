package com.al3xiel.my_finances.feature.pools.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import java.time.LocalDate

@Composable
fun ReceivableRow(
    title: String,
    subtitle: String?,
    amount: Money,
    onClick: () -> Unit,
    subtitleDay: LocalDate? = null,
    detailLabel: String? = null,
    detailAmount: Money? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            subtitleDay?.let {
                Text(dayText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(amount, style = MaterialTheme.typography.bodyLarge)
            if (detailLabel != null && detailAmount != null && detailAmount.isPositive) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(detailLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(detailAmount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReceivableRowPreview() {
    ReceivableRow(
        title = "Perros – mamá",
        subtitle = "Responsable: Mamá",
        amount = Money(11_000, CurrencyCode.PEN),
        onClick = {},
        detailLabel = "Periodo actual:",
        detailAmount = Money(6_000, CurrencyCode.PEN),
    )
}
