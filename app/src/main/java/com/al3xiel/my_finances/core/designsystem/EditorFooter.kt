package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.BusinessError

/**
 * Cierre común de los editores: el error de negocio que rechazó el guardado, si lo hay, y el botón Guardar. Mientras guarda
 * (`saving`) el botón lo dice ("Guardando…") y queda bloqueado; si está bloqueado por otra causa, `disabledReason` la escribe
 * encima del botón en vez de dejarlo apagado sin explicación.
 */
@Composable
fun EditorFooter(
    error: BusinessError?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    saving: Boolean = false,
    disabledReason: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        error?.let {
            Text(
                it.message(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (!enabled && !saving && disabledReason != null) {
            Text(disabledReason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PillButton(
            text = stringResource(if (saving) R.string.editor_saving else R.string.editor_save),
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            loading = saving,
            disabledReason = disabledReason,
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditorFooterPreview() {
    EditorFooter(error = BusinessError.InvalidAmount, onSave = {})
}
