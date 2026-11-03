package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** Título del catálogo; las pruebas lo buscan para saber que compuso. */
const val COMPONENT_CATALOG_TITLE = "Componentes"

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

/**
 * Catálogo de verificación de los componentes del sistema de diseño, juntos y con datos de ejemplo, para revisar en
 * claro, oscuro, AMOLED, fuente grande, montos ocultos y saldo negativo. Solo para previews y pruebas; no es una pantalla.
 */
@Composable
fun ComponentCatalog(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(COMPONENT_CATALOG_TITLE, style = MaterialTheme.typography.headlineMedium)
            MoneyAndBadges()
            Cards()
            Fields()
            Buttons()
        }
    }
}

@Composable
private fun MoneyAndBadges() {
    SectionCard(title = "Montos y estados") {
        SectionRow(headline = "Saldo total", trailing = { MoneyText(pen(1_095_000), variant = MoneyVariant.HERO) })
        SectionDivider()
        SectionRow(headline = "Saldo negativo", trailing = { MoneyText(pen(-5_000), variant = MoneyVariant.NORMAL, tone = MoneyTone.AUTO) })
        SectionDivider()
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(BadgeStatus.PENDING)
            StatusBadge(BadgeStatus.OVERDUE)
            StatusBadge(BadgeStatus.RECONCILED)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(BadgeStatus.NEEDS_REVIEW)
            EstimatedBadge()
        }
    }
}

@Composable
private fun Cards() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AccountCard("BBVA", pen(1_095_000), onClick = {}, typeLabel = "Banco")
        AccountCard("Tarjeta", pen(-5_000), onClick = {}, typeLabel = "Crédito")
        FundProgressCard("Pasajes", pen(5_000), pen(1_200), pen(3_800), isClosed = false, onClick = {})
        MovementRow("Sueldo", pen(500_000), MovementRowKind.INCOME, onClick = {}, subtitle = "BBVA")
        MovementRow("Mercado", pen(3_000), MovementRowKind.EXPENSE, onClick = {}, subtitle = "BBVA · Comida")
        MovementRow("Duplicado", pen(1_000), MovementRowKind.EXPENSE, onClick = {}, isVoided = true)
    }
}

@Composable
private fun Fields() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MoneyInput(value = "12.50", onValueChange = {}, label = "Monto", large = true)
        DateField(label = "Fecha", value = java.time.LocalDate.of(2026, 10, 26), onValueChange = {})
        EntityPicker("Cuenta", listOf(EntityOption("a", "BBVA")), selectedId = "a", onSelect = {})
        EditorFooter(error = null, onSave = {})
    }
}

@Composable
private fun Buttons() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Agregar", onClick = {}, style = PillStyle.TONAL, icon = AppIcons.Add)
        PillButton("Cancelar", onClick = {}, style = PillStyle.TEXT)
    }
}

@Composable
private fun CatalogIn(mode: ThemeMode, fontScale: Float = 1f, hidden: Boolean = false) {
    CompositionLocalProvider(
        LocalDensity provides Density(LocalDensity.current.density, fontScale),
        LocalHideAmounts provides hidden,
    ) {
        MyfinancesTheme(themeMode = mode) { ComponentCatalog() }
    }
}

@Preview(name = "Claro", showBackground = true, heightDp = 1400)
@Composable
private fun CatalogLightPreview() = CatalogIn(ThemeMode.LIGHT)

@Preview(name = "Oscuro", showBackground = true, heightDp = 1400)
@Composable
private fun CatalogDarkPreview() = CatalogIn(ThemeMode.DARK)

@Preview(name = "Oscuro puro", showBackground = true, heightDp = 1400)
@Composable
private fun CatalogAmoledPreview() = CatalogIn(ThemeMode.AMOLED)

@Preview(name = "Fuente 200 %", showBackground = true, heightDp = 2400)
@Composable
private fun CatalogLargeFontPreview() = CatalogIn(ThemeMode.LIGHT, fontScale = 2f)

@Preview(name = "Montos ocultos", showBackground = true, heightDp = 1400)
@Composable
private fun CatalogHiddenPreview() = CatalogIn(ThemeMode.LIGHT, hidden = true)
