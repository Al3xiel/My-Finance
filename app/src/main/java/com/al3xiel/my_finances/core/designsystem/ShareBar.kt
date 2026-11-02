package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money

/**
 * Una fila de la tabla equivalente a la torta: nombre, monto, porcentaje y una barra proporcional. La barra es decoración;
 * el significado va en el texto, así que no depende del color.
 */
@Composable
fun ShareBar(
    label: String,
    amount: Money,
    basisPoints: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val percent = formatBasisPoints(basisPoints)
    val description = stringResource(R.string.report_share_description, label, percent)
    Column(modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            MoneyText(amount, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
            Text(percent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
        }
        Box(
            Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).clearAndSetSemantics { },
        ) {
            Box(Modifier.fillMaxWidth(fraction = (basisPoints / 10_000f).coerceIn(0f, 1f)).height(6.dp).background(color))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShareBarPreview() {
    ShareBar("Comida", Money(60_000, CurrencyCode.PEN), 6_000, MaterialTheme.colorScheme.primary)
}
