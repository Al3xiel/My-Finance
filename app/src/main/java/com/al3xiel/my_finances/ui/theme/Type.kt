package com.al3xiel.my_finances.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.al3xiel.my_finances.R

/** Inter (OFL) empaquetada en `res/font`; una sola fuente variable, con peso por `FontVariation` (D70). */
@OptIn(ExperimentalTextApi::class)
val InterFontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            resId = R.font.inter_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/** Escala M3 completa. */
val AppTypography = Typography(
    displayLarge = style(FontWeight.SemiBold, 57, 64, -0.25),
    displayMedium = style(FontWeight.SemiBold, 45, 52),
    displaySmall = style(FontWeight.SemiBold, 36, 44),
    headlineLarge = style(FontWeight.SemiBold, 32, 40),
    headlineMedium = style(FontWeight.SemiBold, 28, 36),
    headlineSmall = style(FontWeight.SemiBold, 24, 32),
    titleLarge = style(FontWeight.SemiBold, 22, 28),
    titleMedium = style(FontWeight.Medium, 16, 24, 0.15),
    titleSmall = style(FontWeight.Medium, 14, 20, 0.1),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.5),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.25),
    bodySmall = style(FontWeight.Normal, 12, 16, 0.4),
    labelLarge = style(FontWeight.Medium, 14, 20, 0.1),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.5),
    labelSmall = style(FontWeight.Medium, 11, 16, 0.5),
)

/** Estilos de cifras (montos, saldos): con `tnum` las columnas de dígitos quedan alineadas. */
object NumericStyles {
    private const val TABULAR = "tnum"

    val large = style(FontWeight.Bold, 32, 40).copy(fontFeatureSettings = TABULAR)
    val medium = style(FontWeight.SemiBold, 20, 28).copy(fontFeatureSettings = TABULAR)
    val small = style(FontWeight.Medium, 14, 20).copy(fontFeatureSettings = TABULAR)

    fun all(): Map<String, TextStyle> = mapOf("large" to large, "medium" to medium, "small" to small)
}
