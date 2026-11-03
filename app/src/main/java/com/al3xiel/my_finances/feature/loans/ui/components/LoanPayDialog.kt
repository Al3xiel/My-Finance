package com.al3xiel.my_finances.feature.loans.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.AccountAmountFields
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.message
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.feature.loans.ui.LoanPayDialog

/** Pago recibido de una deuda: monto y cuenta de destino; un pago mayor a lo pendiente se rechaza (D1). */
@Composable
fun LoanPayDialogContent(
    dialog: LoanPayDialog,
    accountOptions: List<EntityOption>,
    onAmountChange: (String) -> Unit,
    onAccountSelect: (AccountId) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loan_detail_pay_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountAmountFields(
                    accountLabel = stringResource(R.string.loan_detail_pay_account),
                    accountOptions = accountOptions,
                    accountId = dialog.accountId,
                    onAccountSelect = onAccountSelect,
                    amountText = dialog.amountText,
                    onAmountChange = onAmountChange,
                )
                dialog.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.loan_detail_pay_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catalog_cancel)) } },
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoanPayDialogPreview() {
    LoanPayDialogContent(
        dialog = LoanPayDialog(amountText = "60", accountId = AccountId("bbva"), error = BusinessError.ExceedsPending),
        accountOptions = listOf(EntityOption("bbva", "BBVA")),
        onAmountChange = {}, onAccountSelect = {}, onConfirm = {}, onDismiss = {},
    )
}
