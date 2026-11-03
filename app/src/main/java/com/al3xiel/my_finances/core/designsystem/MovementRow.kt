package com.al3xiel.my_finances.core.designsystem

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money

enum class MovementRowKind { INCOME, EXPENSE, TRANSFER }

/** Fila de la lista de movimientos. Un movimiento anulado se ve tachado y marcado, no desaparece. */
@Composable
fun MovementRow(
    title: String,
    amount: Money,
    kind: MovementRowKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isVoided: Boolean = false,
) {
    val sign = when (kind) {
        MovementRowKind.INCOME -> MoneySign.PLUS
        MovementRowKind.EXPENSE -> MoneySign.MINUS
        MovementRowKind.TRANSFER -> MoneySign.NONE
    }
    val color = when (kind) {
        MovementRowKind.INCOME -> MaterialTheme.colorScheme.primary
        MovementRowKind.EXPENSE -> MaterialTheme.colorScheme.onSurface
        MovementRowKind.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val strike = if (isVoided) TextDecoration.LineThrough else null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (isVoided) 0.6f else 1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, textDecoration = strike)
            val line = listOfNotNull(subtitle, stringResource(R.string.movement_row_voided).takeIf { isVoided })
            if (line.isNotEmpty()) {
                Text(
                    line.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MoneyText(amount, sign = sign, style = MaterialTheme.typography.bodyLarge, color = color, textDecoration = strike)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MovementRowPreview() {
    CompositionLocalProvider(LocalHideAmounts provides false) {
        Column {
            MovementRow("Mercado", Money(3_000, CurrencyCode.PEN), MovementRowKind.EXPENSE, onClick = {}, subtitle = "BBVA · Comida")
            MovementRow("Sueldo", Money(500_000, CurrencyCode.PEN), MovementRowKind.INCOME, onClick = {}, subtitle = "BBVA")
            MovementRow("Retiro", Money(100_000, CurrencyCode.PEN), MovementRowKind.TRANSFER, onClick = {}, subtitle = "BBVA → Efectivo")
            MovementRow("Duplicado", Money(1_000, CurrencyCode.PEN), MovementRowKind.EXPENSE, onClick = {}, isVoided = true)
        }
    }
}
