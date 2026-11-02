package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import java.text.DecimalFormatSymbols

/** Un tramo de torta o barra: `basisPoints` (10 000 = 100%) ya viene calculado por el mayor residuo. */
data class ChartSlice(val label: String, val basisPoints: Int, val color: Color)

/** Giro de cada tramo en grados; los puntos base que suman 10 000 cierran la circunferencia. */
internal fun pieSweeps(basisPoints: List<Int>): List<Float> = basisPoints.map { it * 360f / 10_000f }

/** "33,34 %" con el separador decimal del idioma; solo aritmética entera. */
fun formatBasisPoints(basisPoints: Int, separator: Char = DecimalFormatSymbols.getInstance().decimalSeparator): String {
    val whole = basisPoints / 100
    val fraction = (basisPoints % 100).toString().padStart(2, '0')
    return "$whole$separator$fraction %"
}

/** Paleta de tramos: el color nunca es la única señal; cada tramo va con su fila de texto (nombre y porcentaje). */
@Composable
fun chartPalette(): List<Color> = with(MaterialTheme.colorScheme) {
    listOf(primary, tertiary, secondary, error, primaryContainer, tertiaryContainer, secondaryContainer, outline)
}

/**
 * Torta con `Canvas`. Es solo un dibujo: la tabla equivalente (nombre, monto y porcentaje de cada tramo) la pone quien la usa,
 * y aquí queda su descripción accesible.
 */
@Composable
fun PieChart(slices: List<ChartSlice>, modifier: Modifier = Modifier) {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    val description = stringResource(
        R.string.report_chart_description,
        slices.joinToString("; ") { "${it.label}, ${formatBasisPoints(it.basisPoints, separator)}" },
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val sweeps = pieSweeps(slices.map { it.basisPoints })
    Canvas(modifier.size(160.dp).aspectRatio(1f).semantics { contentDescription = description }) {
        val stroke = size.minDimension * 0.28f
        val inset = stroke / 2f
        val arc = Size(size.minDimension - stroke, size.minDimension - stroke)
        drawCircle(track, radius = arc.width / 2f, style = Stroke(stroke))
        var start = -90f
        slices.forEachIndexed { i, slice ->
            drawArc(
                color = slice.color, startAngle = start, sweepAngle = sweeps[i], useCenter = false,
                topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke),
            )
            start += sweeps[i]
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PieChartPreview() {
    val palette = chartPalette()
    PieChart(listOf(ChartSlice("Comida", 6_000, palette[0]), ChartSlice("Casa", 3_000, palette[1]), ChartSlice("Otros", 1_000, palette[2])))
}
