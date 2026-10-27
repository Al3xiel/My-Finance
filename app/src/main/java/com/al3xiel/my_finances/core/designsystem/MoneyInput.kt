package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.MoneyFormatter

/** Lo que se teclea en un campo de monto: dígitos, un separador y hasta dos decimales. */
internal object MoneyInputFilter {
    private const val MAX_INTEGER_DIGITS = 9

    fun sanitize(raw: String): String {
        val normalized = raw.replace(',', '.')
        val dot = normalized.indexOf('.')
        val integerRaw = (if (dot < 0) normalized else normalized.substring(0, dot)).filter(Char::isDigit)
        if (integerRaw.isEmpty() && dot < 0) return ""
        val integer = integerRaw.trimStart('0').take(MAX_INTEGER_DIGITS).ifEmpty { "0" }
        if (dot < 0) return integer
        val fraction = normalized.substring(dot + 1).filter(Char::isDigit).take(2)
        return "$integer.$fraction"
    }

    /** Céntimos del texto tecleado, o `null` si todavía no es un monto (vacío o solo el separador). */
    fun toMinor(text: String): Long? = MoneyFormatter.parseMinor(text.trimEnd('.'))
}

@Composable
fun MoneyInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    currency: CurrencyCode = CurrencyCode.PEN,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(MoneyInputFilter.sanitize(it)) },
        modifier = modifier,
        label = { Text(label) },
        prefix = { Text(currency.symbol) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MoneyInputPreview() {
    MoneyInput(value = "12.50", onValueChange = {}, label = "Monto")
}
