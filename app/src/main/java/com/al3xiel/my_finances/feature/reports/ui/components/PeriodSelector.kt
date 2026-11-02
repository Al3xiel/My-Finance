package com.al3xiel.my_finances.feature.reports.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.DateChooserDialog
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Texto del periodo: "octubre de 2026", "2026" o "01/10/2026 – 09/10/2026". */
internal fun periodLabel(period: ReportPeriod, locale: Locale): String = when (period) {
    is ReportPeriod.Month -> period.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    is ReportPeriod.Year -> period.year.toString()
    is ReportPeriod.Custom -> {
        val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
        "${period.from.format(f)} – ${period.to.format(f)}"
    }
}

private enum class PickingStep { NONE, FROM, TO }

/**
 * Elige el periodo de un reporte (D36): mes (por defecto), año o rango propio, con flechas para moverse. Nunca deja pasar
 * a un periodo futuro (`ReportPeriod.next(today)` es nulo ahí).
 */
@Composable
fun PeriodSelector(
    period: ReportPeriod,
    today: LocalDate,
    onPeriodChange: (ReportPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(PickingStep.NONE) }
    var pickedFrom by remember { mutableStateOf<LocalDate?>(null) }
    val previous = period.previous()
    val next = period.next(today)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val previousDescription = stringResource(R.string.report_period_previous)
            TextButton(
                onClick = { previous?.let(onPeriodChange) },
                enabled = previous != null,
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { contentDescription = previousDescription },
            ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            Text(periodLabel(period, Locale.getDefault()), style = MaterialTheme.typography.titleMedium)
            val nextDescription = stringResource(R.string.report_period_next)
            TextButton(
                onClick = { next?.let(onPeriodChange) },
                enabled = next != null,
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { contentDescription = nextDescription },
            ) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = period is ReportPeriod.Month,
                onClick = { onPeriodChange(ReportPeriod.Month(YearMonth.from(period.from))) },
                label = { Text(stringResource(R.string.report_period_month)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            FilterChip(
                selected = period is ReportPeriod.Year,
                onClick = { onPeriodChange(ReportPeriod.Year(period.from.year)) },
                label = { Text(stringResource(R.string.report_period_year)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            FilterChip(
                selected = period is ReportPeriod.Custom,
                onClick = { step = PickingStep.FROM },
                label = { Text(stringResource(R.string.report_period_custom)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
    when (step) {
        PickingStep.NONE -> Unit
        PickingStep.FROM -> DateChooserDialog(
            onDateSelected = { pickedFrom = it; step = PickingStep.TO },
            onDismiss = { step = PickingStep.NONE },
            maxDate = today,
            initialDate = period.from,
        )
        PickingStep.TO -> DateChooserDialog(
            onDateSelected = { to ->
                val from = pickedFrom ?: period.from
                onPeriodChange(ReportPeriod.Custom(from, if (to.isBefore(from)) from else to))
                step = PickingStep.NONE
            },
            onDismiss = { step = PickingStep.NONE },
            maxDate = today,
            initialDate = period.closingOn(today),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PeriodSelectorPreview() {
    PeriodSelector(ReportPeriod.Month(YearMonth.of(2026, 10)), LocalDate.of(2026, 11, 2), onPeriodChange = {})
}
