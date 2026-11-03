package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.domain.model.BusinessError

/**
 * Marco de los editores de registro: el contenido se desplaza y el pie con "Guardar" queda fijo al fondo,
 * por encima del teclado abierto.
 */
@Composable
fun EditorLayout(
    error: BusinessError?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        EditorFooter(
            error = error,
            onSave = onSave,
            enabled = saveEnabled,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditorLayoutPreview() {
    EditorLayout(error = null, onSave = {}) { Text("Contenido") }
}
