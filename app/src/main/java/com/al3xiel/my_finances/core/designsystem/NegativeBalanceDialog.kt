package com.al3xiel.my_finances.core.designsystem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.BusinessError

/** Aviso previo a guardar algo que deja el saldo negativo (D14): se permite, pero se confirma. */
@Composable
fun NegativeBalanceDialog(warning: BusinessError.WouldGoNegative, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_negative_title)) },
        text = { Text(warning.message()) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.editor_negative_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catalog_cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun NegativeBalanceDialogPreview() {
    NegativeBalanceDialog(BusinessError.WouldGoNegative(-5_000), onConfirm = {}, onDismiss = {})
}
