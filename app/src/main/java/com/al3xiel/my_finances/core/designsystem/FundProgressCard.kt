package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.ui.theme.LocalFinanceColors
import com.al3xiel.my_finances.ui.theme.LocalSpacing

/**
 * Tarjeta de un fondo: barra de lo gastado sobre lo asignado y las tres cifras. El restante siempre se rotula
 * "estimado" (U4): sale de estimaciones, no de un conteo.
 */
@Composable
fun FundProgressCard(
    name: String,
    allocated: Money,
    spent: Money,
    remaining: Money,
    isClosed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (allocated.amountMinor <= 0) 0f else (spent.amountMinor.toFloat() / allocated.amountMinor).coerceIn(0f, 1f)
    val overspent = remaining.amountMinor < 0
    val spacing = LocalSpacing.current
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (isClosed) StatusBadge(BadgeStatus.PENDING, label = stringResource(R.string.fund_closed_badge))
            }
            // La barra es decorativa: las cifras de abajo ya dicen lo mismo, así que no entra al foco de accesibilidad.
            LinearProgressIndicator(
                progress = { fraction },
                color = if (overspent) LocalFinanceColors.current.expense else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Figure(stringResource(R.string.fund_progress_allocated), allocated)
                Figure(stringResource(R.string.fund_progress_spent), spent)
                Figure(stringResource(R.string.fund_progress_remaining_estimated), remaining, tone = MoneyTone.AUTO)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                EstimatedBadge()
                if (overspent) {
                    Text(
                        stringResource(R.string.fund_overspent_warning),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalFinanceColors.current.expense,
                    )
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, money: Money, tone: MoneyTone = MoneyTone.NONE) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(money, style = MaterialTheme.typography.bodyMedium, tone = tone)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FundProgressCardPreview() {
    CompositionLocalProvider(LocalHideAmounts provides false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val pen = { minor: Long -> Money(minor, CurrencyCode.PEN) }
            FundProgressCard("Pasajes", pen(5_000), pen(1_200), pen(3_800), isClosed = false, onClick = {})
            FundProgressCard("Viejo", pen(1_000), pen(1_000), pen(0), isClosed = true, onClick = {})
        }
    }
}
