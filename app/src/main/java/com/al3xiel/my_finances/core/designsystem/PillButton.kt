package com.al3xiel.my_finances.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.CircularProgressIndicator
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** `DANGER` es para lo que borra o no se puede deshacer: el color de error con su texto, nunca solo rojo sobre fondo. */
enum class PillStyle { PRIMARY, TONAL, TEXT, DANGER }

private val PillShape = RoundedCornerShape(percent = 50)
private val PillPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)

/**
 * Botón en píldora; siempre de al menos 48 dp de alto para el toque. El icono es opcional y decorativo.
 * `loading` lo bloquea y muestra un indicador de progreso junto al texto (que dice qué está pasando); `disabledReason` es lo que
 * un lector de pantalla anuncia cuando el botón está bloqueado, para que nadie quede sin saber por qué.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.PRIMARY,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    loading: Boolean = false,
    disabledReason: String? = null,
) {
    val active = enabled && !loading
    val explained = if (!active && disabledReason != null && !loading) {
        Modifier.semantics { stateDescription = disabledReason }
    } else {
        Modifier
    }
    val sized = modifier.heightIn(min = 48.dp).then(explained)
    val label: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            icon?.takeIf { !loading }?.let {
                Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text)
        }
    }
    when (style) {
        PillStyle.PRIMARY -> Button(onClick, sized, active, shape = PillShape, contentPadding = PillPadding) { label() }
        PillStyle.TONAL -> FilledTonalButton(onClick, sized, active, shape = PillShape, contentPadding = PillPadding) { label() }
        PillStyle.DANGER -> Button(
            onClick, sized, active, shape = PillShape, contentPadding = PillPadding,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
        ) { label() }
        PillStyle.TEXT -> TextButton(onClick, sized, active, shape = PillShape, contentPadding = PillPadding) { label() }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun PillButtonPreview() {
    MyfinancesTheme {
        androidx.compose.foundation.layout.Column {
            PillButton("Guardar", onClick = {})
            PillButton("Agregar", onClick = {}, style = PillStyle.TONAL, icon = AppIcons.Add)
            PillButton("Cancelar", onClick = {}, style = PillStyle.TEXT)
            PillButton("Desactivado", onClick = {}, enabled = false)
        }
    }
}
