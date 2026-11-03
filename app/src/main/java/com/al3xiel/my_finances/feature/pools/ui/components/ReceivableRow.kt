package com.al3xiel.my_finances.feature.pools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.core.designsystem.StatusBadge
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** Estado que acompaña a una fila de Por cobrar: insignia de la tabla de estados (§7) y, si hace falta, su texto propio. */
data class ReceivableStatus(val badge: BadgeStatus, val label: String? = null)

/** Fila de Por cobrar dentro de una `SectionCard`: nombre, apoyo, monto pendiente y el estado en texto. */
@Composable
fun ReceivableRow(
    title: String,
    subtitle: String?,
    amount: Money,
    onClick: () -> Unit,
    subtitleDay: LocalDate? = null,
    detailLabel: String? = null,
    detailAmount: Money? = null,
    status: ReceivableStatus? = null,
) {
    val support = listOfNotNull(subtitle, subtitleDay?.let { dayText(it) }).joinToString(" · ").ifEmpty { null }
    SectionRow(
        headline = title,
        supporting = support,
        onClick = onClick,
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MoneyText(amount, style = MaterialTheme.typography.bodyLarge)
                if (detailLabel != null && detailAmount != null && detailAmount.isPositive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(detailLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(detailAmount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                status?.let { StatusBadge(it.badge, label = it.label) }
            }
        },
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ReceivableRowPreview() {
    MyfinancesTheme {
        SectionCard(title = "Cúmulos", modifier = Modifier.padding(16.dp)) {
            ReceivableRow(
                title = "Perros – mamá",
                subtitle = "Responsable: Mamá",
                amount = Money(11_000, CurrencyCode.PEN),
                onClick = {},
                detailLabel = "Periodo actual:",
                detailAmount = Money(6_000, CurrencyCode.PEN),
                status = ReceivableStatus(BadgeStatus.PENDING),
            )
        }
    }
}
