package com.al3xiel.my_finances.feature.pools.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.StatusBadge
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import java.time.LocalDate
import com.al3xiel.my_finances.feature.pools.domain.model.PoolPeriod
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

@Composable
fun PeriodCard(period: PoolPeriod, title: String?) {
    val spacing = LocalSpacing.current
    SectionCard(title = title) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            val range = period.closedOn?.let { stringResource(R.string.pool_period_range, dayText(period.openedOn), dayText(it)) }
                ?: stringResource(R.string.pool_period_since, dayText(period.openedOn))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(range, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                StatusBadge(period.status.badge(), label = stringResource(period.status.labelRes()))
            }
            // La barra es decorativa: lo reembolsado y lo pendiente van en texto justo debajo.
            val fraction = if (period.total.amountMinor <= 0) 0f else (period.reimbursed.amountMinor.toFloat() / period.total.amountMinor).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
            AmountLine(stringResource(R.string.pool_period_total), period.total)
            AmountLine(stringResource(R.string.pool_period_reimbursed), period.reimbursed)
            AmountLine(stringResource(R.string.pool_period_pending), period.pending)
        }
    }
}

@Composable
private fun AmountLine(label: String, amount: Money) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        MoneyText(amount, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Insignia de la tabla de estados (§7): abierto y rendido esperan; saldado ya está. */
internal fun PoolPeriodStatus.badge() = when (this) {
    PoolPeriodStatus.OPEN, PoolPeriodStatus.STATEMENT_SENT -> BadgeStatus.PENDING
    PoolPeriodStatus.SETTLED -> BadgeStatus.RECONCILED
}

private fun PoolPeriodStatus.labelRes() = when (this) {
    PoolPeriodStatus.OPEN -> R.string.pool_status_open
    PoolPeriodStatus.STATEMENT_SENT -> R.string.pool_status_statement_sent
    PoolPeriodStatus.SETTLED -> R.string.pool_status_settled
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun PeriodCardPreview() {
    MyfinancesTheme { PeriodCard(
        PoolPeriod(
            PoolPeriodId("dogs-p1"), PoolId("dogs"), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
            PoolPeriodStatus.STATEMENT_SENT, null, Money(20_000, CurrencyCode.PEN), Money(15_000, CurrencyCode.PEN),
        ),
        title = null,
    ) }
}
