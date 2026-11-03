package com.al3xiel.my_finances.feature.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money

/** Los cuatro indicadores de Inicio (docs/04 §5.5). Disponible libre va en rojo y con aviso si es negativo. */
@Composable
fun HomeIndicators(
    accountsBalance: Money,
    reserved: Money,
    freeAvailable: Money,
    receivable: Money,
    modifier: Modifier = Modifier,
) {
    val negative = freeAvailable.amountMinor < 0
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Indicator(stringResource(R.string.home_indicator_balance), accountsBalance, Modifier.weight(1f))
                Indicator(stringResource(R.string.home_indicator_reserved), reserved, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Indicator(
                    stringResource(R.string.home_indicator_free),
                    freeAvailable,
                    Modifier.weight(1f),
                    color = if (negative) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
                Indicator(stringResource(R.string.home_indicator_receivable), receivable, Modifier.weight(1f))
            }
            if (negative) {
                Text(
                    stringResource(R.string.home_free_negative_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Indicator(label: String, money: Money, modifier: Modifier, color: Color = Color.Unspecified) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(money, style = MaterialTheme.typography.titleLarge, color = color)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeIndicatorsPreview() {
    val pen = { minor: Long -> Money(minor, CurrencyCode.PEN) }
    CompositionLocalProvider(LocalHideAmounts provides false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeIndicators(pen(1_095_000), pen(150_000), pen(945_000), pen(23_000))
            HomeIndicators(pen(100_000), pen(150_000), pen(-50_000), pen(0))
        }
    }
}
