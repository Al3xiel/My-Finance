package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.FundId

private const val NO_FUND = ""

/** Selector opcional de fondo: sin elegir uno, el dinero no se aparta ni se descuenta de ningún fondo. */
@Composable
fun FundPicker(
    label: String,
    options: List<EntityOption>,
    selectedId: FundId?,
    onSelect: (FundId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    EntityPicker(
        label = label,
        options = listOf(EntityOption(NO_FUND, stringResource(R.string.fund_picker_none))) + options,
        selectedId = selectedId?.value ?: NO_FUND,
        onSelect = { onSelect(it.takeIf { id -> id != NO_FUND }?.let(::FundId)) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FundPickerPreview() {
    FundPicker("Fondo", listOf(EntityOption("fare", "Pasajes")), selectedId = FundId("fare"), onSelect = {})
}
