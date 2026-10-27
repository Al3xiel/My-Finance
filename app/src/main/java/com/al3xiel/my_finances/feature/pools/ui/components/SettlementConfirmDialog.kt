package com.al3xiel.my_finances.feature.pools.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.event.PoolKind

/** Aviso previo a rendir (D17): en un cúmulo permanente cierra el periodo y abre otro. */
@Composable
fun SettlementConfirmDialog(kind: PoolKind?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.statement_confirm_title)) },
        text = {
            Text(stringResource(if (kind == PoolKind.EVENT) R.string.statement_confirm_event else R.string.statement_confirm_permanent))
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.statement_confirm_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catalog_cancel)) } },
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettlementConfirmDialogPreview() {
    SettlementConfirmDialog(PoolKind.PERMANENT, onConfirm = {}, onDismiss = {})
}
