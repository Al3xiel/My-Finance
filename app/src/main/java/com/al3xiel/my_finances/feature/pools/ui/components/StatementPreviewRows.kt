package com.al3xiel.my_finances.feature.pools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.al3xiel.my_finances.domain.model.PartId
import java.time.LocalDate
import com.al3xiel.my_finances.feature.pools.domain.model.StatementLine

@Composable
fun StatementLineRow(line: StatementLine) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text((listOf(line.categoryName) + line.tagNames).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
            Text(dayText(line.occurredOn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoneyText(line.pending, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun TotalRow(label: String, amount: Money, emphasis: Boolean = false) {
    val style = if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        MoneyText(amount, style = style)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatementPreviewRowsPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        StatementLineRow(
            StatementLine(PartId("p1"), LocalDate.of(2026, 10, 3), "Mascotas", listOf("Perros"), Money(12_000, CurrencyCode.PEN), Money(12_000, CurrencyCode.PEN)),
        )
        TotalRow("Total a reembolsar", Money(12_000, CurrencyCode.PEN), emphasis = true)
    }
}
