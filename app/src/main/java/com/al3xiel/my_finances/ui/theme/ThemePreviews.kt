package com.al3xiel.my_finances.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.core.designsystem.AppIcons
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode

/** Etiqueta de la galería; las pruebas la buscan para saber que compuso. */
const val GALLERY_TITLE = "Paleta"

/**
 * Galería de verificación del sistema visual: colores, tipos, formas e iconos juntos, para revisar en claro, oscuro,
 * AMOLED y con fuente al 200 %. Solo para previews y pruebas; no es una pantalla de la app ni usa datos reales.
 */
@Composable
fun ThemeGallery(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(LocalSpacing.current.lg),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.lg),
    ) {
        Section(GALLERY_TITLE) { SchemeSwatches() }
        Section("Finanzas") { FinanceSwatches() }
        Section("Tipos") { TypeSamples() }
        Section("Formas") { ShapeSamples() }
        Section("Iconos") { IconSamples() }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun Swatch(label: String, color: Color, onColor: Color) {
    Box(
        modifier = Modifier.size(width = 112.dp, height = 56.dp).clip(MaterialTheme.shapes.small).background(color).padding(LocalSpacing.current.sm),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(label, color = onColor, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SchemeSwatches() {
    val c = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm), verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
        Swatch("primary", c.primary, c.onPrimary)
        Swatch("primaryContainer", c.primaryContainer, c.onPrimaryContainer)
        Swatch("secondary", c.secondary, c.onSecondary)
        Swatch("tertiary", c.tertiary, c.onTertiary)
        Swatch("error", c.error, c.onError)
        Swatch("background", c.background, c.onBackground)
        Swatch("surface", c.surfaceContainer, c.onSurface)
        Swatch("surfaceVariant", c.surfaceVariant, c.onSurfaceVariant)
        Swatch("brand", BrandTurquoise, Color.Black)
    }
}

@Composable
private fun FinanceSwatches() {
    val f = LocalFinanceColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm), verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
        Swatch("income", f.income, f.onIncome)
        Swatch("expense", f.expense, f.onExpense)
        Swatch("warning", f.warning, f.onWarning)
        Swatch("estimated", f.estimated, f.onEstimated)
        Swatch("neutral", f.neutral, f.onNeutral)
        Swatch("incomeContainer", f.incomeContainer, f.onIncomeContainer)
        Swatch("expenseContainer", f.expenseContainer, f.onExpenseContainer)
    }
}

@Composable
private fun TypeSamples() {
    val t = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs)) {
        listOf<Pair<String, TextStyle>>(
            "displaySmall" to t.displaySmall, "headlineMedium" to t.headlineMedium, "titleLarge" to t.titleLarge,
            "titleMedium" to t.titleMedium, "bodyLarge" to t.bodyLarge, "bodyMedium" to t.bodyMedium, "labelLarge" to t.labelLarge,
        ).forEach { (name, style) -> Text(name, style = style) }
        // Con cifras tabulares los dígitos de cada columna quedan alineados.
        listOf("1,111.11", "8,888.88", "S/ 1,234.50").forEach { Text(it, style = NumericStyles.medium) }
    }
}

@Composable
private fun ShapeSamples() {
    val s = MaterialTheme.shapes
    FlowRow(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm), verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
        listOf<Pair<String, Shape>>("12" to s.small, "16" to s.medium, "24" to s.large, "28" to s.extraLarge).forEach { (dp, shape) ->
            Box(
                modifier = Modifier.size(72.dp).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(dp, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun IconSamples() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.lg), verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
        AppIcons.all.values.forEach { Icon(painterResource(it), contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun GalleryIn(mode: ThemeMode, fontScale: Float = 1f) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        MyfinancesTheme(themeMode = mode) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().height(900.dp)) { ThemeGallery() }
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Composable
private fun GalleryLightPreview() = GalleryIn(ThemeMode.LIGHT)

@Preview(name = "Oscuro", showBackground = true)
@Composable
private fun GalleryDarkPreview() = GalleryIn(ThemeMode.DARK)

@Preview(name = "Oscuro puro", showBackground = true)
@Composable
private fun GalleryAmoledPreview() = GalleryIn(ThemeMode.AMOLED)

@Preview(name = "Fuente 200 %", showBackground = true)
@Composable
private fun GalleryLargeFontPreview() = GalleryIn(ThemeMode.LIGHT, fontScale = 2f)
