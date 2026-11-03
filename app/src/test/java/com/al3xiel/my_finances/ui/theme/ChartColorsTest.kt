package com.al3xiel.my_finances.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Paleta categórica de los gráficos: tramos que se distinguen entre sí y de su fondo. Para objetos gráficos el mínimo de
 * WCAG (1.4.11) es 3:1; el texto de cada tramo va aparte, con su propio contraste.
 */
class ChartColorsTest {
    private fun channel(value: Float): Double =
        value.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** CIE L*a*b* (D65), para medir cuánto se parecen dos colores a la vista. */
    private fun lab(c: Color): Triple<Double, Double, Double> {
        val r = channel(c.red)
        val g = channel(c.green)
        val b = channel(c.blue)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        val f = { t: Double -> if (t > 0.008856) t.pow(1.0 / 3) else 7.787 * t + 16.0 / 116 }
        return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    private fun distance(a: Color, b: Color): Double {
        val (l1, a1, b1) = lab(a)
        val (l2, a2, b2) = lab(b)
        return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    private val palettes = mapOf(
        "light" to (ChartColors.Light to listOf(LightColors.background, LightColors.surface)),
        "dark" to (ChartColors.Dark to listOf(DarkColors.background, DarkColors.surface)),
        "amoled" to (ChartColors.Dark to listOf(AmoledColors.background, AmoledColors.surface)),
    )

    @Test fun `each palette has at least ten colors`() {
        assertTrue(ChartColors.Light.size >= 10)
        assertTrue(ChartColors.Dark.size >= 10)
    }

    @Test fun `every color holds three to one against the backgrounds it is drawn on`() {
        palettes.forEach { (name, pair) ->
            val (colors, backgrounds) = pair
            colors.forEachIndexed { i, color ->
                backgrounds.forEach { bg ->
                    val ratio = contrast(color, bg)
                    assertTrue("$name color $i contrasts only %.2f".format(ratio), ratio >= 3.0)
                }
            }
        }
    }

    @Test fun `neighbors never look alike, wrapping around the end`() {
        listOf("light" to ChartColors.Light, "dark" to ChartColors.Dark).forEach { (name, colors) ->
            colors.indices.forEach { i ->
                val next = colors[(i + 1) % colors.size]
                val d = distance(colors[i], next)
                assertTrue("$name $i and next differ only %.1f".format(d), d >= 40.0)
            }
        }
    }

    @Test fun `no two colors of a palette are nearly the same`() {
        listOf("light" to ChartColors.Light, "dark" to ChartColors.Dark).forEach { (name, colors) ->
            for (i in colors.indices) for (j in i + 1 until colors.size) {
                val d = distance(colors[i], colors[j])
                assertTrue("$name $i and $j differ only %.1f".format(d), d >= 15.0)
            }
        }
    }

    @Test fun `each scheme gets the palette made for its background`() {
        assertEquals(ChartColors.Light, chartColorsFor(SchemeKind.LIGHT))
        assertEquals(ChartColors.Light, chartColorsFor(SchemeKind.DYNAMIC_LIGHT))
        assertEquals(ChartColors.Dark, chartColorsFor(SchemeKind.DARK))
        assertEquals(ChartColors.Dark, chartColorsFor(SchemeKind.AMOLED))
        assertEquals(ChartColors.Dark, chartColorsFor(SchemeKind.DYNAMIC_DARK))
        assertEquals(ChartColors.Dark, chartColorsFor(SchemeKind.DYNAMIC_AMOLED))
    }
}
