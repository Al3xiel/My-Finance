package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import java.time.LocalDate

/**
 * Campo de solo lectura que muestra la fecha del hecho y abre el selector al tocarlo. Para accesibilidad es un solo
 * botón ("Fecha: 26 oct 2026"): el campo de texto de debajo no entra en el foco ni se lee dos veces.
 */
@Composable
fun DateField(
    label: String,
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    maxDate: LocalDate? = null,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val shown = dayText(value)

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics { contentDescription = "$label: $shown" }
                .clickable(role = Role.Button, onClickLabel = label) { picking = true },
        )
    }
    if (picking) {
        DateChooserDialog(
            onDateSelected = {
                picking = false
                onValueChange(it)
            },
            onDismiss = { picking = false },
            maxDate = maxDate,
            initialDate = value,
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DateFieldPreview() {
    DateField(label = "Fecha", value = LocalDate.of(2026, 10, 26), onValueChange = {})
}
