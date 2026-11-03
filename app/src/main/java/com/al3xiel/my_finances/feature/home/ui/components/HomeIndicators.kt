package com.al3xiel.my_finances.feature.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.MoneyTone
import com.al3xiel.my_finances.core.designsystem.MoneyVariant
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money

/**
 * Los cuatro indicadores de Inicio (docs/04 §5.5; D74): el saldo en cuentas es el héroe y los otros tres van en
 * tarjetas pequeñas. Disponible libre va en rojo y con aviso si es negativo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeIndicators(
    accountsBalance: Money,
    reserved: Money,
    freeAvailable: Money,
    receivable: Money,
    modifier: Modifier = Modifier,
) {
    val negative = freeAvailable.amountMinor < 0
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.home_indicator_balance),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                MoneyText(
                    accountsBalance,
                    variant = MoneyVariant.HERO,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallIndicator(stringResource(R.string.home_indicator_reserved), reserved)
            SmallIndicator(stringResource(R.string.home_indicator_free), freeAvailable, tone = MoneyTone.AUTO)
            SmallIndicator(stringResource(R.string.home_indicator_receivable), receivable)
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

@Composable
private fun SmallIndicator(label: String, money: Money, tone: MoneyTone = MoneyTone.NONE) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(min = 96.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(money, variant = MoneyVariant.SMALL, tone = tone)
        }
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
