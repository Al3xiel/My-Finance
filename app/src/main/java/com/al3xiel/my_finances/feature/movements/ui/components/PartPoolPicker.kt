package com.al3xiel.my_finances.feature.movements.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.domain.model.PoolId

private const val NO_POOL = ""

/** Selector de cúmulo de una parte del gasto: elegir uno la deja como gasto que alguien te reembolsará. */
@Composable
fun PartPoolPicker(
    options: List<EntityOption>,
    selectedId: PoolId?,
    onSelect: (PoolId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    EntityPicker(
        label = stringResource(R.string.expense_part_pool),
        options = listOf(EntityOption(NO_POOL, stringResource(R.string.expense_part_no_pool))) + options,
        selectedId = selectedId?.value ?: NO_POOL,
        onSelect = { onSelect(it.takeIf { id -> id != NO_POOL }?.let(::PoolId)) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PartPoolPickerPreview() {
    PartPoolPicker(listOf(EntityOption("dogs", "Perros – mamá")), selectedId = PoolId("dogs"), onSelect = {})
}
