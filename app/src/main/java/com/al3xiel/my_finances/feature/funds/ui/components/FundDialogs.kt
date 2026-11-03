package com.al3xiel.my_finances.feature.funds.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy

/** Recarga con dinero propio: el monto se valida en el caso de uso, aquí solo se teclea. */
@Composable
fun TopUpDialog(onConfirm: (amountText: String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var amount by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.fund_top_up_title)) },
        text = { MoneyInput(value = amount, onValueChange = { amount = it }, label = stringResource(R.string.fund_top_up_amount)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(amount) }, enabled = amount.isNotEmpty()) { Text(stringResource(R.string.fund_top_up)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catalog_cancel)) } },
    )
}

/** Cierre: devolver el sobrante al disponible o pasarlo a otro fondo activo (que se elige aquí). */
@Composable
fun CloseFundDialog(
    targets: List<EntityOption>,
    onConfirm: (RemainderPolicy, FundId?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var policy by rememberSaveable { mutableStateOf(RemainderPolicy.RELEASE_TO_AVAILABLE) }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    val rollOver = policy == RemainderPolicy.ROLL_OVER
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.fund_close_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PolicyRow(stringResource(R.string.fund_close_release), selected = !rollOver) { policy = RemainderPolicy.RELEASE_TO_AVAILABLE }
                if (targets.isNotEmpty()) {
                    PolicyRow(stringResource(R.string.fund_close_roll_over), selected = rollOver) { policy = RemainderPolicy.ROLL_OVER }
                }
                if (rollOver) {
                    EntityPicker(
                        label = stringResource(R.string.fund_close_target),
                        options = targets,
                        selectedId = target,
                        onSelect = { target = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(policy, if (rollOver) target?.let(::FundId) else null) }, enabled = !rollOver || target != null) {
                Text(stringResource(R.string.fund_close_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catalog_cancel)) } },
    )
}

@Composable
private fun PolicyRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TopUpDialogPreview() {
    TopUpDialog(onConfirm = {}, onDismiss = {})
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CloseFundDialogPreview() {
    CloseFundDialog(targets = listOf(EntityOption("food", "Comida")), onConfirm = { _, _ -> }, onDismiss = {})
}
