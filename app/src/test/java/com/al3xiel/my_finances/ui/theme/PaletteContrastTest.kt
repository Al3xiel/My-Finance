package com.al3xiel.my_finances.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class PaletteContrastTest {
    private fun channel(value: Float): Double =
        value.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Matiz en grados (0–360). */
    private fun hue(c: Color): Double {
        val top = max(c.red, max(c.green, c.blue))
        val bottom = min(c.red, min(c.green, c.blue))
        val d = (top - bottom).toDouble()
        if (d == 0.0) return 0.0
        val h = when (top) {
            c.red -> ((c.green - c.blue) / d) % 6
            c.green -> (c.blue - c.red) / d + 2
            else -> (c.red - c.green) / d + 4
        }
        return (h * 60 + 360) % 360
    }

    private fun hueGap(a: Color, b: Color) = abs(hue(a) - hue(b)).let { min(it, 360 - it) }

    private fun ColorScheme.pairs() = listOf(
        "primary" to (primary to onPrimary),
        "primaryContainer" to (primaryContainer to onPrimaryContainer),
        "secondary" to (secondary to onSecondary),
        "secondaryContainer" to (secondaryContainer to onSecondaryContainer),
        "tertiary" to (tertiary to onTertiary),
        "tertiaryContainer" to (tertiaryContainer to onTertiaryContainer),
        "error" to (error to onError),
        "errorContainer" to (errorContainer to onErrorContainer),
        "background" to (background to onBackground),
        "surface" to (surface to onSurface),
        "surfaceVariant" to (surfaceVariant to onSurfaceVariant),
        "inverseSurface" to (inverseSurface to inverseOnSurface),
    )

    private fun FinanceColors.pairs() = listOf(
        "income" to (income to onIncome), "incomeContainer" to (incomeContainer to onIncomeContainer),
        "expense" to (expense to onExpense), "expenseContainer" to (expenseContainer to onExpenseContainer),
        "warning" to (warning to onWarning), "warningContainer" to (warningContainer to onWarningContainer),
        "estimated" to (estimated to onEstimated), "estimatedContainer" to (estimatedContainer to onEstimatedContainer),
        "neutral" to (neutral to onNeutral), "neutralContainer" to (neutralContainer to onNeutralContainer),
    )

    private val schemes = mapOf("light" to LightColors, "dark" to DarkColors, "amoled" to AmoledColors)

    @Test fun `every scheme pair reaches AA`() {
        schemes.forEach { (name, scheme) ->
            scheme.pairs().forEach { (pair, colors) ->
                val ratio = contrast(colors.first, colors.second)
                assertTrue("$name $pair contrast $ratio", ratio >= 4.5)
            }
        }
    }

    @Test fun `every finance pair reaches AA`() {
        mapOf("light" to FinanceColors.Light, "dark" to FinanceColors.Dark).forEach { (name, finance) ->
            finance.pairs().forEach { (pair, colors) ->
                val ratio = contrast(colors.first, colors.second)
                assertTrue("$name $pair contrast $ratio", ratio >= 4.5)
            }
        }
    }

    @Test fun `finance accents are readable on the surface of their scheme`() {
        listOf(
            Triple("light", FinanceColors.Light, LightColors.surface),
            Triple("dark", FinanceColors.Dark, DarkColors.surface),
            Triple("amoled", FinanceColors.Dark, AmoledColors.surface),
        ).forEach { (name, f, surface) ->
            listOf("income" to f.income, "expense" to f.expense, "warning" to f.warning, "estimated" to f.estimated, "neutral" to f.neutral)
                .forEach { (accent, color) -> assertTrue("$name $accent on surface", contrast(color, surface) >= 4.5) }
        }
    }

    @Test fun `income cannot be confused with the turquoise or with the expense`() {
        listOf("light" to (FinanceColors.Light to LightColors), "dark" to (FinanceColors.Dark to DarkColors)).forEach { (name, p) ->
            val (finance, scheme) = p
            assertTrue("$name income vs primary", hueGap(finance.income, scheme.primary) >= 40)
            assertTrue("$name income vs expense", hueGap(finance.income, finance.expense) >= 90)
        }
    }

    @Test fun `amoled paints the background and surface pure black`() {
        assertEquals(Color(0xFF000000), AmoledColors.background)
        assertEquals(Color(0xFF000000), AmoledColors.surface)
    }

    @Test fun `the brand turquoise is an accent, not a text color`() {
        assertEquals(Color(0xFF00A5A8), BrandTurquoise)
        assertTrue(contrast(Color.White, BrandTurquoise) < 4.5) // por eso el primario de texto es más oscuro
        assertTrue(contrast(Color.White, LightColors.primary) >= 4.5)
    }
}
