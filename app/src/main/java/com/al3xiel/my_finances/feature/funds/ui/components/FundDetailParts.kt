package com.al3xiel.my_finances.feature.funds.ui.components

import com.al3xiel.my_finances.core.designsystem.progressFraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.AppIcons
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.core.designsystem.DateField
import com.al3xiel.my_finances.core.designsystem.EstimatedBadge
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.MoneyTone
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.core.designsystem.StatusBadge
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
import com.al3xiel.my_finances.ui.theme.LocalFinanceColors
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

@Composable
internal fun FundHeader(overview: FundOverview) {
    val fund = overview.fund
    val spacing = LocalSpacing.current
    val spent = overview.detailedSpent + fund.untrackedSpent
    val fraction = progressFraction(spent, fund.allocated)
    val overspent = overview.estimatedRemaining.amountMinor < 0
    SectionCard {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    fund.endsOn?.let { stringResource(R.string.fund_detail_range, dayText(fund.startsOn), dayText(it)) }
                        ?: stringResource(R.string.fund_detail_range_open, dayText(fund.startsOn)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (fund.status == FundStatus.CLOSED) StatusBadge(BadgeStatus.PENDING, label = stringResource(R.string.fund_closed_badge))
            }
            // Decorativa: las cifras de abajo dicen lo mismo en texto.
            LinearProgressIndicator(
                progress = { fraction },
                color = if (overspent) LocalFinanceColors.current.expense else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            )
            Line(stringResource(R.string.fund_progress_allocated), fund.allocated)
            Line(stringResource(R.string.fund_detail_detailed_spent), overview.detailedSpent)
            Line(stringResource(R.string.fund_detail_untracked_spent), fund.untrackedSpent)
            Line(stringResource(R.string.fund_progress_remaining_estimated), overview.estimatedRemaining, tone = MoneyTone.AUTO)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                EstimatedBadge()
                if (overspent) {
                    Text(stringResource(R.string.fund_overspent_warning), style = MaterialTheme.typography.labelMedium, color = LocalFinanceColors.current.expense)
                }
            }
            if (fund.mode == ConsumptionMode.DAILY_ESTIMATE) {
                fund.dailyEstimate?.let { Line(stringResource(R.string.fund_detail_estimate), it) }
                Text(stringResource(R.string.fund_detail_usage_days, overview.usageDays), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun Line(label: String, money: Money, tone: MoneyTone = MoneyTone.NONE) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        MoneyText(money, style = MaterialTheme.typography.bodyMedium, tone = tone)
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
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        PillButton(stringResource(R.string.fund_add_expense), { onAddExpense(id) }, Modifier.fillMaxWidth(), icon = AppIcons.Add)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
            PillButton(stringResource(R.string.fund_top_up), onTopUp, Modifier.weight(1f), PillStyle.TONAL)
            PillButton(stringResource(R.string.fund_count), { onCount(id) }, Modifier.weight(1f), PillStyle.TONAL)
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            PillButton(stringResource(R.string.fund_edit), { onEdit(id) }, style = PillStyle.TEXT)
            PillButton(stringResource(R.string.fund_close), onClose, style = PillStyle.TEXT)
        }
    }
}

@Composable
internal fun UsageDayRow(day: LocalDate, today: LocalDate, marked: Boolean, onDay: (LocalDate) -> Unit, onToggle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        DateField(stringResource(R.string.fund_mark_day), day, onDay, modifier = Modifier.weight(1f), maxDate = today)
        PillButton(stringResource(if (marked) R.string.fund_toggle_day_unmark else R.string.fund_toggle_day_mark), onToggle, style = PillStyle.TONAL)
    }
}

@Composable
internal fun CountRow(count: FundCountRecord) {
    SectionRow(
        headline = dayText(count.occurredOn),
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(count.declaredRemaining, style = MaterialTheme.typography.bodyMedium)
                MoneyText(count.realSpent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                count.deviation?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.fund_count_deviation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun FundDetailPartsPreview() {
    val fund = Fund(
        FundId("fare"), "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), LocalDate.of(2026, 10, 1), null,
        pen(5_000), pen(300), FundStatus.ACTIVE, null, null, null,
    )
    MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FundHeader(FundOverview(fund, pen(1_200), 3, null, pen(3_500), pen(1_500), pen(3_500)))
            FundActions(fund.id, {}, {}, {}, {}, {})
            UsageDayRow(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 27), marked = false, onDay = {}, onToggle = {})
            CountRow(FundCountRecord(pen(3_800), pen(1_200), pen(1_000), pen(200), LocalDate.of(2026, 10, 20)))
        }
    } }
}
