package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.BusinessError

/** Diálogo de alta o renombrado de un catálogo; `extra` agrega campos propios (tipo, persona…). */
@Composable
fun NameDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.catalog_name),
    error: BusinessError? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(label) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    isError = error != null,
                    supportingText = error?.let { { Text(it.message()) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                extra()
            }
        },
        confirmButton = {
            PillButton(stringResource(R.string.catalog_save), onClick = onConfirm, enabled = name.isNotBlank())
        },
        dismissButton = { PillButton(stringResource(R.string.catalog_cancel), onClick = onDismiss, style = PillStyle.TEXT) },
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NameDialogPreview() {
    NameDialog(
        title = "Nueva categoría",
        name = "Comida",
        onNameChange = {},
        onConfirm = {},
        onDismiss = {},
        error = BusinessError.DuplicateName,
    )
}
