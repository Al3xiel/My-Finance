package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.text.formatBasisPoints
import com.al3xiel.my_finances.ui.theme.LocalChartColors
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Un tramo de torta o barra: `basisPoints` (10 000 = 100%) ya viene calculado por el mayor residuo. */
data class ChartSlice(val label: String, val basisPoints: Int, val color: Color)

/** Etiqueta de prueba del anillo, para simular toques. */
const val PIE_CHART_RING_TAG = "pie_chart_ring"

/** Un tramo más angosto que esto no se rotula en el dibujo (no cabría); sigue en la lista de texto y en la descripción. */
internal const val MIN_DIRECT_LABEL_BASIS_POINTS = 600

private val RingSize = 160.dp
private val LabelGap = 8.dp
private val LabelSpacing = 4.dp

/** Giro de cada tramo en grados; los puntos base que suman 10 000 cierran la circunferencia. */
internal fun pieSweeps(basisPoints: List<Int>): List<Float> = basisPoints.map { it * 360f / 10_000f }

/** Qué tramo contiene un ángulo medido en sentido horario desde arriba; `null` si cae fuera de todos. */
internal fun pieSliceIndexAt(basisPoints: List<Int>, clockwiseDegreesFromTop: Float): Int? {
    val angle = ((clockwiseDegreesFromTop % 360f) + 360f) % 360f
    var end = 0f
    pieSweeps(basisPoints).forEachIndexed { i, sweep ->
        if (sweep <= 0f) return@forEachIndexed
        end += sweep
        if (angle < end) return i
    }
    return null
}

/** Los tramos con ancho suficiente para llevar su nombre y su porcentaje junto al dibujo. */
internal fun directLabelIndices(basisPoints: List<Int>, minBasisPoints: Int = MIN_DIRECT_LABEL_BASIS_POINTS): List<Int> =
    basisPoints.indices.filter { basisPoints[it] >= minBasisPoints }

/** El texto del tramo: nombre y porcentaje. El monto va en la lista debajo, donde respeta "ocultar montos". */
internal fun pieLabelText(label: String, basisPoints: Int): String = "$label ${formatBasisPoints(basisPoints)}"

/**
 * Reparte etiquetas en vertical sin que se pisen: cada una quiere su centro, pero ninguna queda arriba del borde ni a menos
 * de `gap` de la anterior. Devuelve el borde superior de cada una, en el orden de entrada.
 */
internal fun spreadLabels(desiredCenters: List<Float>, heights: List<Float>, gap: Float): List<Float> {
    val tops = FloatArray(desiredCenters.size)
    var previousBottom = Float.NEGATIVE_INFINITY
    desiredCenters.indices.sortedBy { desiredCenters[it] }.forEach { i ->
        val top = max(max(desiredCenters[i] - heights[i] / 2f, previousBottom + gap), 0f)
        tops[i] = top
        previousBottom = top + heights[i]
    }
    return tops.toList()
}

/** Paleta de tramos del tema (ver `ChartColors`); el color nunca es la única señal. */
@Composable
fun chartPalette(): List<Color> = LocalChartColors.current

/**
 * Torta con `Canvas` y sus etiquetas directas: cada tramo ancho lleva su nombre y porcentaje pegado al dibujo, en vez de una
 * leyenda aparte. Con `onSliceClick`, tocar el anillo o una etiqueta avisa qué tramo es (los reportes lo usan para filtrar).
 * La tabla equivalente (nombre, monto y porcentaje de cada tramo) la pone quien la usa; aquí queda además la descripción
 * accesible con todos los tramos, también los angostos.
 */
@Composable
fun PieChart(slices: List<ChartSlice>, modifier: Modifier = Modifier, onSliceClick: ((Int) -> Unit)? = null) {
    val description = stringResource(
        R.string.report_chart_description,
        slices.joinToString("; ") { "${it.label}, ${formatBasisPoints(it.basisPoints)}" },
    )
    val basisPoints = remember(slices) { slices.map { it.basisPoints } }
    val labelled = remember(basisPoints) { directLabelIndices(basisPoints) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val sweeps = remember(basisPoints) { pieSweeps(basisPoints) }
    val starts = remember(sweeps) { sweeps.runningFold(-90f) { acc, s -> acc + s } }

    Layout(
        modifier = modifier,
        content = {
            val ringModifier = Modifier
                .layoutId(RING_ID)
                .testTag(PIE_CHART_RING_TAG)
                .semantics { contentDescription = description }
                .then(
                    if (onSliceClick == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(basisPoints, onSliceClick) {
                            detectTapGestures { tap ->
                                val dx = tap.x - size.width / 2f
                                val dy = tap.y - size.height / 2f
                                val outer = size.width / 2f
                                val inner = outer - outer * 2f * RING_STROKE_RATIO
                                val radius = hypot(dx, dy)
                                if (radius in inner * 0.9f..outer) {
                                    val degrees = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                                    pieSliceIndexAt(basisPoints, degrees)?.let(onSliceClick)
                                }
                            }
                        }
                    },
                )
            Canvas(ringModifier) {
                val stroke = size.minDimension * RING_STROKE_RATIO
                val inset = stroke / 2f
                val arc = Size(size.minDimension - stroke, size.minDimension - stroke)
                drawCircle(track, radius = arc.width / 2f, style = Stroke(stroke))
                slices.forEachIndexed { i, slice ->
                    drawArc(
                        color = slice.color, startAngle = starts[i], sweepAngle = sweeps[i], useCenter = false,
                        topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke),
                    )
                }
            }
            labelled.forEach { i ->
                SliceLabel(slices[i], onClick = onSliceClick?.let { click -> { click(i) } }, modifier = Modifier.layoutId(i))
            }
        },
    ) { measurables, constraints ->
        val ringPx = RingSize.roundToPx()
        val gapPx = LabelGap.roundToPx()
        val spacingPx = LabelSpacing.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else ringPx * 3
        val ringLeft = (width - ringPx) / 2
        val labelRoom = max((width - ringPx) / 2 - gapPx, 0)

        val ring = measurables.first { it.layoutId == RING_ID }.measure(Constraints.fixed(ringPx, ringPx))
        val labels = labelled.map { i ->
            i to measurables.first { it.layoutId == i }.measure(Constraints(maxWidth = labelRoom))
        }

        // Cada etiqueta quiere quedar a la altura del punto medio de su tramo, del lado donde cae.
        val sides = labels.map { (i, _) ->
            val mid = Math.toRadians((starts[i] + sweeps[i] / 2f).toDouble())
            val onRight = cos(mid) >= 0.0
            val centerY = ringPx / 2f + (sin(mid) * ringPx / 2f).toFloat()
            onRight to centerY
        }
        val tops = FloatArray(labels.size)
        listOf(true, false).forEach { rightSide ->
            val ids = labels.indices.filter { sides[it].first == rightSide }
            val placed = spreadLabels(ids.map { sides[it].second }, ids.map { labels[it].second.height.toFloat() }, spacingPx.toFloat())
            ids.forEachIndexed { k, idx -> tops[idx] = placed[k] }
        }
        val height = max(ringPx, labels.indices.maxOfOrNull { (tops[it] + labels[it].second.height).toInt() } ?: 0)

        layout(width, height) {
            ring.placeRelative(ringLeft, 0)
            labels.forEachIndexed { k, (_, placeable) ->
                val x = if (sides[k].first) ringLeft + ringPx + gapPx else ringLeft - gapPx - placeable.width
                placeable.placeRelative(x, tops[k].toInt())
            }
        }
    }
}

private const val RING_ID = "pie_ring"
private const val RING_STROKE_RATIO = 0.28f

@Composable
private fun SliceLabel(slice: ChartSlice, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val base = if (onClick == null) modifier else modifier.heightIn(min = 48.dp).clickable(onClick = onClick)
    Row(base.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        // La muestra de color solo enlaza el texto con su tramo; el nombre y el porcentaje ya lo dicen todo.
        Box(Modifier.size(10.dp).background(slice.color, CircleShape))
        Text(
            pieLabelText(slice.label, slice.basisPoints),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun PieChartPreview() {
    MyfinancesTheme {
        val palette = chartPalette()
        PieChart(
            listOf(
                ChartSlice("Comida", 4_000, palette[0]),
                ChartSlice("Casa", 3_000, palette[1]),
                ChartSlice("Transporte", 1_800, palette[2]),
                ChartSlice("Salud", 900, palette[3]),
                ChartSlice("Otros", 300, palette[4]),
            ),
            Modifier.fillMaxWidth(),
        )
    }
}
