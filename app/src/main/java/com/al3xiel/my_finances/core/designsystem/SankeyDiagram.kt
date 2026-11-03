package com.al3xiel.my_finances.core.designsystem

import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R

/** Un nodo del diagrama; `valueMinor` es lo que pasa por él. */
data class SankeyBox(val key: String, val label: String, val valueMinor: Long)

/** Un flujo entre dos nodos de columnas contiguas. */
data class SankeyFlow(val fromKey: String, val toKey: String, val valueMinor: Long)

internal data class SankeyNodeBox(val key: String, val column: Int, val top: Float, val height: Float)

/**
 * Altura de cada nodo proporcional a su valor, con la misma escala en todas las columnas: la que hace caber la columna más
 * alta (suma de valores más los huecos de su cantidad de nodos) en `totalHeight`. Cada columna se alinea arriba.
 */
internal fun sankeyLayout(columns: List<List<SankeyBox>>, totalHeight: Float, gap: Float): Map<String, SankeyNodeBox> {
    val maxTotal = columns.maxOfOrNull { column -> column.sumOf { it.valueMinor } } ?: 0L
    if (maxTotal <= 0L) return emptyMap()
    val maxGaps = (columns.maxOfOrNull { it.size } ?: 1) - 1
    val scale = ((totalHeight - gap * maxGaps) / maxTotal).coerceAtLeast(0f)
    val result = LinkedHashMap<String, SankeyNodeBox>()
    columns.forEachIndexed { index, column ->
        var top = 0f
        column.forEach { box ->
            val height = box.valueMinor * scale
            result[box.key] = SankeyNodeBox(box.key, index, top, height)
            top += height + gap
        }
    }
    return result
}

/** Un color de la paleta por nodo, en orden de lectura (columna a columna), para que dos nodos vecinos no se confundan. */
internal fun sankeyNodeColors(columns: List<List<SankeyBox>>, palette: List<Color>): Map<String, Color> {
    if (palette.isEmpty()) return emptyMap()
    val colors = LinkedHashMap<String, Color>()
    columns.flatten().forEachIndexed { index, box -> colors[box.key] = palette[index % palette.size] }
    return colors
}

/**
 * Sankey con `Canvas` (columnas de nodos unidas por bandas) y, debajo, la tabla equivalente con cada flujo y su monto: el
 * dibujo es solo apoyo visual y su descripción accesible resume los mismos flujos. Sin flujos no dibuja nada.
 */
@Composable
fun SankeyDiagram(
    columns: List<List<SankeyBox>>,
    flows: List<SankeyFlow>,
    amountLabel: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    if (flows.isEmpty()) return
    val labels = columns.flatten().associate { it.key to it.label }
    val rows = flows.map {
        Triple(stringResource(R.string.origin_sankey_flow, labels[it.fromKey] ?: it.fromKey, labels[it.toKey] ?: it.toKey), amountLabel(it.valueMinor), it)
    }
    val description = stringResource(R.string.origin_sankey_description, rows.joinToString("; ") { "${it.first}, ${it.second}" })
    val palette = chartPalette()
    val nodeColors = sankeyNodeColors(columns, palette)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = description }) {
            val nodeWidth = 12.dp.toPx()
            val layout = sankeyLayout(columns, size.height, gap = 8.dp.toPx())
            val step = if (columns.size > 1) (size.width - nodeWidth) / (columns.size - 1) else 0f
            val outOffset = HashMap<String, Float>()
            val inOffset = HashMap<String, Float>()
            flows.forEach { flow ->
                val from = layout[flow.fromKey] ?: return@forEach
                val to = layout[flow.toKey] ?: return@forEach
                val fromScale = from.height / (columns[from.column].first { it.key == flow.fromKey }.valueMinor.coerceAtLeast(1))
                val toScale = to.height / (columns[to.column].first { it.key == flow.toKey }.valueMinor.coerceAtLeast(1))
                val thickness = flow.valueMinor * fromScale
                val thicknessTo = flow.valueMinor * toScale
                val y0 = from.top + (outOffset[flow.fromKey] ?: 0f)
                val y1 = to.top + (inOffset[flow.toKey] ?: 0f)
                outOffset[flow.fromKey] = (outOffset[flow.fromKey] ?: 0f) + thickness
                inOffset[flow.toKey] = (inOffset[flow.toKey] ?: 0f) + thicknessTo
                val x0 = from.column * step + nodeWidth
                val x1 = to.column * step
                val mid = (x0 + x1) / 2f
                val band = Path().apply {
                    moveTo(x0, y0)
                    cubicTo(mid, y0, mid, y1, x1, y1)
                    lineTo(x1, y1 + thicknessTo)
                    cubicTo(mid, y1 + thicknessTo, mid, y0 + thickness, x0, y0 + thickness)
                    close()
                }
                drawPath(band, (nodeColors[flow.fromKey] ?: palette.first()).copy(alpha = 0.35f))
            }
            layout.values.forEach { node ->
                val color = nodeColors[node.key] ?: palette.first()
                drawRect(color, Offset(node.column * step, node.top), Size(nodeWidth, node.height.coerceAtLeast(2f)))
                val text = measurer.measure(
                    labels[node.key].orEmpty(), labelStyle, overflow = TextOverflow.Ellipsis, maxLines = 1,
                    constraints = Constraints(maxWidth = (step - nodeWidth - 4.dp.toPx()).coerceAtLeast(40.dp.toPx()).toInt()),
                )
                val onRight = node.column < columns.lastIndex
                val x = if (onRight) node.column * step + nodeWidth + 2.dp.toPx() else node.column * step - text.size.width - 2.dp.toPx()
                if (node.height >= text.size.height) drawText(text, topLeft = Offset(x.coerceAtLeast(0f), node.top))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEach { (label, amount, flow) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // La muestra enlaza la fila con su banda; el texto ya dice de dónde a dónde va.
                    Box(Modifier.size(10.dp).background(nodeColors[flow.fromKey] ?: palette.first(), CircleShape))
                    Text(label, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    Text(amount, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun SankeyDiagramPreview() {
    MyfinancesTheme {
    SankeyDiagram(
        columns = listOf(
            listOf(SankeyBox("s1", "Sueldo", 50), SankeyBox("s2", "Freelance", 10)),
            listOf(SankeyBox("a", "BBVA", 60)),
            listOf(SankeyBox("c", "Comida", 40), SankeyBox("t", "Transporte", 20)),
        ),
        flows = listOf(SankeyFlow("s1", "a", 50), SankeyFlow("s2", "a", 10), SankeyFlow("a", "c", 40), SankeyFlow("a", "t", 20)),
        amountLabel = { "S/ $it" },
    )
    }
}
