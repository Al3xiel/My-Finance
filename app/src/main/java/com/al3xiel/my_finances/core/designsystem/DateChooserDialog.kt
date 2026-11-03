package com.al3xiel.my_finances.core.designsystem

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.al3xiel.my_finances.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** El `DatePicker` de Material trabaja en milisegundos UTC a medianoche; aquí se traduce a `LocalDate`. */
internal fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Elige una fecha del hecho; `maxDate` impide las futuras (docs/02 §5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateChooserDialog(
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    maxDate: LocalDate? = null,
    initialDate: LocalDate? = null,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.toPickerMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = maxDate == null || utcTimeMillis <= maxDate.toPickerMillis()
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onDateSelected(it.toPickerDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.dialog_date_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.catalog_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@Preview(showBackground = true)
@Composable
private fun DateChooserDialogPreview() {
    DateChooserDialog(onDateSelected = {}, onDismiss = {}, maxDate = LocalDate.of(2026, 10, 26))
}
