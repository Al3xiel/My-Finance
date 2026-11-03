package com.al3xiel.my_finances.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores categóricos de los gráficos (tortas, barras, Sankey). Doce tonos, ordenados para que dos vecinos nunca se parezcan
 * (la lista se recorre en orden y repite al pasar de doce). Cada paleta se hizo para su fondo: la clara aguanta 3:1 contra el
 * fondo claro, la oscura contra el oscuro y el AMOLED negro. El color solo refuerza: cada tramo lleva su nombre y su porcentaje.
 */
object ChartColors {
    val Light: List<Color> = listOf(
        Color(0xFFC10B66), Color(0xFF009E7D), Color(0xFFBD46B4), Color(0xFF646800),
        Color(0xFF108CFE), Color(0xFFD93C3F), Color(0xFF0F6F66), Color(0xFFEE4CA3),
        Color(0xFF258912), Color(0xFF3F58CD), Color(0xFFE2662C), Color(0xFF0581A5),
    )

    val Dark: List<Color> = listOf(
        Color(0xFFFE8178), Color(0xFF39EE97), Color(0xFFEAA0FE), Color(0xFF92B222),
        Color(0xFFA9D7FD), Color(0xFFFFA940), Color(0xFF00BC95), Color(0xFFFEBFE5),
        Color(0xFF74D15A), Color(0xFF46ACFC), Color(0xFFFFC882), Color(0xFF21CDEA),
    )
}

/** Paleta clara con fondo claro; oscura y AMOLED comparten la oscura. Los esquemas dinámicos siguen su luminosidad. */
fun chartColorsFor(kind: SchemeKind): List<Color> = when (kind) {
    SchemeKind.LIGHT, SchemeKind.DYNAMIC_LIGHT -> ChartColors.Light
    SchemeKind.DARK, SchemeKind.AMOLED, SchemeKind.DYNAMIC_DARK, SchemeKind.DYNAMIC_AMOLED -> ChartColors.Dark
}

val LocalChartColors = staticCompositionLocalOf { ChartColors.Light }
