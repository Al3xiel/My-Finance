package com.al3xiel.my_finances.core.designsystem

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.money.MoneyFormatter
import com.al3xiel.my_finances.ui.theme.LocalFinanceColors
import com.al3xiel.my_finances.ui.theme.NumericStyles

enum class MoneySign { NONE, PLUS, MINUS }

/** Tamaño de una cifra: héroe (saldo de portada), normal y pequeña. Todas con dígitos tabulares. */
enum class MoneyVariant { HERO, NORMAL, SMALL }

/** Color semántico opcional; `AUTO` pinta de gasto solo lo negativo. Nunca es la única señal: el signo siempre se muestra. */
enum class MoneyTone {
    NONE, INCOME, EXPENSE, AUTO;

    internal fun resolve(money: Money): MoneyTone = if (this == AUTO) (if (money.amountMinor < 0) EXPENSE else NONE) else this
}

internal fun MoneyVariant.style(): TextStyle = when (this) {
    MoneyVariant.HERO -> NumericStyles.large
    MoneyVariant.NORMAL -> NumericStyles.medium
    MoneyVariant.SMALL -> NumericStyles.small
}

internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

internal const val AMOUNT_MASK = "••••"

internal fun moneyDisplayText(money: Money, hidden: Boolean, sign: MoneySign = MoneySign.NONE): String {
    val body = if (hidden) "${money.currency.symbol} $AMOUNT_MASK" else MoneyFormatter.format(money)
    return when (sign) {
        MoneySign.NONE -> body
        MoneySign.PLUS -> "+$body"
        MoneySign.MINUS -> "−$body"
    }
}

/** Todo monto de la app se pinta con esto: respeta "ocultar montos" (`LocalHideAmounts`) en un solo lugar. */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    sign: MoneySign = MoneySign.NONE,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
    variant: MoneyVariant? = null,
    tone: MoneyTone = MoneyTone.NONE,
) {
    val finance = LocalFinanceColors.current
    val toneColor = when (tone.resolve(money)) {
        MoneyTone.INCOME -> finance.income
        MoneyTone.EXPENSE -> finance.expense
        else -> Color.Unspecified
    }
    val hidden = LocalHideAmounts.current
    val hiddenDescription = stringResource(R.string.money_hidden_description)
    Text(
        text = moneyDisplayText(money, hidden, sign),
        modifier = if (hidden) modifier.semantics { contentDescription = hiddenDescription } else modifier,
        style = (variant?.style() ?: style).tabular(),
        color = if (color != Color.Unspecified) color else toneColor,
        textDecoration = textDecoration,
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MoneyTextPreview() {
    androidx.compose.foundation.layout.Column {
        CompositionLocalProvider(LocalHideAmounts provides false) {
            MoneyText(Money(123_456, CurrencyCode.PEN), sign = MoneySign.PLUS)
        }
        CompositionLocalProvider(LocalHideAmounts provides true) {
            MoneyText(Money(123_456, CurrencyCode.PEN))
        }
    }
}
