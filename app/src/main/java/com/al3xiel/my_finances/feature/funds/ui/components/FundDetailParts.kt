package com.al3xiel.my_finances.feature.funds.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.DateField
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.feature.funds.domain.model.Fund
import com.al3xiel.my_finances.feature.funds.domain.model.FundCountRecord
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import java.time.LocalDate

@Composable
internal fun FundHeader(overview: FundOverview) {
    val fund = overview.fund
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(fund.name, style = MaterialTheme.typography.titleLarge)
        Text(
            fund.endsOn?.let { stringResource(R.string.fund_detail_range, dayText(fund.startsOn), dayText(it)) }
                ?: stringResource(R.string.fund_detail_range_open, dayText(fund.startsOn)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (fund.status == FundStatus.CLOSED) Text(stringResource(R.string.fund_closed_badge), style = MaterialTheme.typography.labelLarge)
        Line(stringResource(R.string.fund_progress_allocated), fund.allocated)
        Line(stringResource(R.string.fund_detail_detailed_spent), overview.detailedSpent)
        Line(stringResource(R.string.fund_detail_untracked_spent), fund.untrackedSpent)
        Line(stringResource(R.string.fund_progress_remaining_estimated), overview.estimatedRemaining)
        if (fund.mode == ConsumptionMode.DAILY_ESTIMATE) {
            fund.dailyEstimate?.let { Line(stringResource(R.string.fund_detail_estimate), it) }
            Text(stringResource(R.string.fund_detail_usage_days, overview.usageDays), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun Line(label: String, money: Money) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        MoneyText(money, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun FundActions(
    id: FundId,
    onCount: (FundId) -> Unit,
    onEdit: (FundId) -> Unit,
    onAddExpense: (FundId) -> Unit,
    onTopUp: () -> Unit,
    onClose: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onAddExpense(id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fund_add_expense)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onTopUp, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.fund_top_up)) }
            Button(onClick = { onCount(id) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.fund_count)) }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { onEdit(id) }) { Text(stringResource(R.string.fund_edit)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.fund_close)) }
        }
    }
}

@Composable
internal fun UsageDayRow(day: LocalDate, today: LocalDate, marked: Boolean, onDay: (LocalDate) -> Unit, onToggle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        DateField(stringResource(R.string.fund_mark_day), day, onDay, modifier = Modifier.weight(1f), maxDate = today)
        OutlinedButton(onClick = onToggle) {
            Text(stringResource(if (marked) R.string.fund_toggle_day_unmark else R.string.fund_toggle_day_mark))
        }
    }
}

@Composable
internal fun CountRow(count: FundCountRecord) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(dayText(count.occurredOn), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            MoneyText(count.declaredRemaining, style = MaterialTheme.typography.bodyMedium)
            MoneyText(count.realSpent, style = MaterialTheme.typography.bodyMedium)
        }
        count.deviation?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.fund_count_deviation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}


private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FundDetailPartsPreview() {
    val fund = Fund(
        FundId("fare"), "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), LocalDate.of(2026, 10, 1), null,
        pen(5_000), pen(300), FundStatus.ACTIVE, null, null, null,
    )
    CompositionLocalProvider(LocalHideAmounts provides false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FundHeader(FundOverview(fund, pen(1_200), 3, null, pen(3_500), pen(1_500), pen(3_500)))
            FundActions(fund.id, {}, {}, {}, {}, {})
            UsageDayRow(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 27), marked = false, onDay = {}, onToggle = {})
            CountRow(FundCountRecord(pen(3_800), pen(1_200), pen(1_000), pen(200), LocalDate.of(2026, 10, 20)))
        }
    }
}
