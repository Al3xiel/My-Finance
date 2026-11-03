package com.al3xiel.my_finances.feature.reconciliation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.MoneySign
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus

/** Una cifra con su rótulo, alineada a los extremos. */
@Composable
internal fun LabeledMoney(label: String, money: Money, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        MoneyText(money, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Banco menos app, con su signo: positivo = al banco le sobra (+), negativo = le falta (−). */
@Composable
internal fun DifferenceText(difference: Money, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    when {
        difference.isZero -> Text(stringResource(R.string.reconciliation_balanced), modifier = modifier, style = style)
        difference.isPositive -> MoneyText(difference, modifier, MoneySign.PLUS, style)
        else -> MoneyText(Money(Math.negateExact(difference.amountMinor), difference.currency), modifier, MoneySign.MINUS, style)
    }
}

internal fun ReconciliationCause.labelRes() = when (this) {
    ReconciliationCause.BALANCED -> R.string.reconciliation_cause_balanced
    ReconciliationCause.MISSING_RECORD -> R.string.reconciliation_cause_missing_record
    ReconciliationCause.RECORDING_ERROR -> R.string.reconciliation_cause_recording_error
    ReconciliationCause.SUSPECTED_APP_ERROR -> R.string.reconciliation_cause_suspected_app_error
    ReconciliationCause.UNKNOWN -> R.string.reconciliation_cause_unknown
}

internal fun ReconciliationStatus.labelRes() = when (this) {
    ReconciliationStatus.OPEN -> R.string.reconciliation_status_open
    ReconciliationStatus.RESOLVED -> R.string.reconciliation_status_resolved
    ReconciliationStatus.CANCELLED -> R.string.reconciliation_status_cancelled
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReconciliationPartsPreview() {
    androidx.compose.foundation.layout.Column {
        LabeledMoney("Saldo del banco", Money(81_240, CurrencyCode.PEN))
        DifferenceText(Money(240, CurrencyCode.PEN))
        DifferenceText(Money(-1_500, CurrencyCode.PEN))
        DifferenceText(Money(0, CurrencyCode.PEN))
    }
}
