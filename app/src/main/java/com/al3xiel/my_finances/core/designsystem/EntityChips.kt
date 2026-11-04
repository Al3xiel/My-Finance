package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Selector de una entidad del catálogo en chips que se acomodan en varias líneas (sin desplazamiento horizontal).
 * `onSelect` también se llama al tocar el chip ya elegido; quien lo usa decide si eso deselecciona. `emptyHint` se muestra
 * cuando no hay ninguna opción.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntityChips(
    label: String,
    options: List<EntityOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyHint: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Sin opciones el selector no puede quedar mudo: dice que está vacío y dónde se crea lo que falta.
        if (options.isEmpty() && emptyHint != null) {
            Text(emptyHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option.id == selectedId,
                    onClick = { onSelect(option.id) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EntityChipsPreview() {
    EntityChips("Cuenta", listOf(EntityOption("a", "BBVA"), EntityOption("b", "Efectivo")), selectedId = "a", onSelect = {})
}
