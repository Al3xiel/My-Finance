package com.al3xiel.my_finances.core.designsystem

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

enum class PillStyle { PRIMARY, TONAL, TEXT }

private val PillShape = RoundedCornerShape(percent = 50)
private val PillPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)

/** Botón en píldora; siempre de al menos 48 dp de alto para el toque. El icono es opcional y decorativo. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.PRIMARY,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val sized = modifier.heightIn(min = 48.dp)
    val label: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text)
        }
    }
    when (style) {
        PillStyle.PRIMARY -> Button(onClick, sized, enabled, shape = PillShape, contentPadding = PillPadding) { label() }
        PillStyle.TONAL -> FilledTonalButton(onClick, sized, enabled, shape = PillShape, contentPadding = PillPadding) { label() }
        PillStyle.TEXT -> TextButton(onClick, sized, enabled, shape = PillShape, contentPadding = PillPadding) { label() }
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
