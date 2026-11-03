package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/**
 * Marco común de las pantallas de catálogo (cuentas, categorías, conceptos, personas, fuentes): encabezado grande con
 * "Agregar" a la derecha y el contenido en tarjetas; vacío, muestra el estado vacío con su acción sugerida.
 */
@Composable
fun CatalogScaffold(
    title: String,
    onAdd: () -> Unit,
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenScaffold(
        title = title,
        modifier = modifier,
        actions = { PillButton(text = stringResource(R.string.catalog_add), onClick = onAdd, style = PillStyle.TEXT) },
    ) { padding ->
        if (isEmpty) {
            EmptyState(
                title = stringResource(R.string.catalog_empty_title),
                body = stringResource(R.string.catalog_empty_body),
                modifier = Modifier.padding(padding),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

/** Filas de catálogo en una sola tarjeta, con un separador fino entre una y otra. */
@Composable
fun <T> CatalogCard(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    SectionCard(modifier = modifier) {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            row(item)
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CatalogScaffoldPreview() {
    MyfinancesTheme {
        CatalogScaffold(title = "Personas", onAdd = {}, isEmpty = false) {
            CatalogCard(listOf("Mamá", "Papá")) { Text(it, modifier = Modifier.padding(16.dp)) }
        }
    }
}
