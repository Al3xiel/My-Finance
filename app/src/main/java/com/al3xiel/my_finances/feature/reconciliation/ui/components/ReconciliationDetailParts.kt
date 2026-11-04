package com.al3xiel.my_finances.feature.reconciliation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.feature.movements.ui.labelRes
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationMovement
import java.time.LocalDate

/** Lista de movimientos de S61; `actionLabel` es el botón de cada fila (Corregir o Vincular) y es opcional. */
@Composable
internal fun MovementSection(
    title: String,
    movements: List<ReconciliationMovement>,
    emptyText: String,
    actionLabel: String?,
    onAction: (ReconciliationMovement) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    SectionCard(title = title, modifier = modifier) {
        hint?.let { SectionRow(headline = it) }
        if (movements.isEmpty() && emptyText.isNotEmpty()) SectionRow(headline = emptyText)
        movements.forEachIndexed { index, movement ->
            if (index > 0 || hint != null) SectionDivider()
            MovementLine(movement, actionLabel, onAction)
        }
    }
}

@Composable
private fun MovementLine(movement: ReconciliationMovement, actionLabel: String?, onAction: (ReconciliationMovement) -> Unit) {
    SectionRow(
        headline = stringResource(movement.type.labelRes()),
        supporting = listOfNotNull(dayText(movement.occurredOn), movement.note).joinToString(" · "),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoneyText(movement.amount, style = MaterialTheme.typography.bodyMedium)
                if (actionLabel != null) PillButton(actionLabel, { onAction(movement) }, style = PillStyle.TEXT)
            }
        },
    )
}

/** Corregir solo aplica a ingresos y gastos (D12, D25, D33). */
internal fun ReconciliationMovement.isCorrectable() = type == MovementType.INCOME || type == MovementType.EXPENSE

/**
 * "Registrar movimiento olvidado" con el tipo a elegir; el editor se abre ya vinculado a la conciliación. El menú se ancla al
 * contenedor del botón: sin él se ancla a la columna entera de la pantalla y aparece lejos, arriba.
 */
@Composable
internal fun RegisterForgottenButton(onRegister: (MovementType) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        PillButton(stringResource(R.string.reconciliation_register_forgotten), { expanded = true }, Modifier.fillMaxWidth(), PillStyle.TONAL)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(MovementType.INCOME, MovementType.EXPENSE, MovementType.TRANSFER).forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.labelRes())) },
                    onClick = {
                        expanded = false
                        onRegister(type)
                    },
                )
            }
        }
    }
}

@Composable
internal fun ResolveButton(onResolve: () -> Unit, modifier: Modifier = Modifier) {
    PillButton(stringResource(R.string.reconciliation_resolve), onResolve, modifier.fillMaxWidth())
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ReconciliationDetailPartsPreview() {
    val movement = ReconciliationMovement(
        MovementId("m1"), MovementType.INCOME, AccountId("bcp"), null, Money(1_000, CurrencyCode.PEN),
        LocalDate.of(2026, 10, 25), "Reembolso", MovementStatus.ACTIVE,
    )
    MyfinancesTheme {
        Column {
            MovementSection("Vinculados", listOf(movement), "Nada", "Corregir", {})
            RegisterForgottenButton({})
            ResolveButton({})
        }
    }
}
