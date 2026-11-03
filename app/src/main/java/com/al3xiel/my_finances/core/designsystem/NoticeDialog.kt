package com.al3xiel.my_finances.core.designsystem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.BusinessError

/** Aviso de una regla de negocio rechazada en una acción que no tiene formulario (archivar, anular…). */
@Composable
fun NoticeDialog(error: BusinessError, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(error.message()) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_accept)) } },
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NoticeDialogPreview() {
    NoticeDialog(BusinessError.BalanceNotZero, onDismiss = {})
}
