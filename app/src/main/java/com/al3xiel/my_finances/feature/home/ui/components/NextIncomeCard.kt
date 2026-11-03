package com.al3xiel.my_finances.feature.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange
import java.time.LocalDate

/** El próximo ingreso esperado: el vencido más antiguo o el programado más cercano. */
@Composable
fun NextIncomeCard(income: ExpectedIncome, overdue: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_next_income_range, dayText(income.range.from), dayText(income.range.to)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (overdue) {
                    Text(
                        stringResource(R.string.home_next_income_overdue),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            MoneyText(income.estimated, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NextIncomeCardPreview() {
    val income = ExpectedIncome(
        ExpectedIncomeId("e"), IncomeSourceId("s"), AccountId("a"), Money(250_000, CurrencyCode.PEN),
        ExpectedRange(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 30)), true, ExpectedIncomeStatus.SCHEDULED, null, null, null,
    )
    CompositionLocalProvider(LocalHideAmounts provides false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NextIncomeCard(income, overdue = false, onClick = {})
            NextIncomeCard(income, overdue = true, onClick = {})
        }
    }
}
