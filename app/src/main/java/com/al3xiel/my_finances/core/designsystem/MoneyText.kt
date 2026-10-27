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

enum class MoneySign { NONE, PLUS, MINUS }

private const val MASK = "••••"

internal fun moneyDisplayText(money: Money, hidden: Boolean, sign: MoneySign = MoneySign.NONE): String {
    val body = if (hidden) "${money.currency.symbol} $MASK" else MoneyFormatter.format(money)
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
) {
    val hidden = LocalHideAmounts.current
    val hiddenDescription = stringResource(R.string.money_hidden_description)
    Text(
        text = moneyDisplayText(money, hidden, sign),
        modifier = if (hidden) modifier.semantics { contentDescription = hiddenDescription } else modifier,
        style = style,
        color = color,
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
