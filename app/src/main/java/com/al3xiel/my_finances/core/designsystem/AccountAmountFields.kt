package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.AccountId

/** Cuenta y monto, el par que repiten los editores de dinero que entra o sale de una cuenta. */
@Composable
fun AccountAmountFields(
    accountLabel: String,
    accountOptions: List<EntityOption>,
    accountId: AccountId?,
    onAccountSelect: (AccountId) -> Unit,
    amountText: String,
    onAmountChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EntityPicker(
            label = accountLabel,
            options = accountOptions,
            selectedId = accountId?.value,
            onSelect = { onAccountSelect(AccountId(it)) },
            modifier = Modifier.fillMaxWidth(),
        )
        MoneyInput(
            value = amountText,
            onValueChange = onAmountChange,
            label = stringResource(R.string.editor_amount),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountAmountFieldsPreview() {
    AccountAmountFields("Cuenta de destino", listOf(EntityOption("bbva", "BBVA")), AccountId("bbva"), {}, "50", {})
}
