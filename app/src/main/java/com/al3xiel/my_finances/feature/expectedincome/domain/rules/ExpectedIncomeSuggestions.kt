package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import java.time.LocalDate

/** Un ingreso esperado pendiente que un ingreso nuevo podría confirmar ("¿confirma Sueldo ~S/ 2,000?"). */
data class ExpectedIncomeSuggestion(
    val id: ExpectedIncomeId,
    val sourceName: String,
    val estimated: Money,
    val expectedFrom: LocalDate,
    val expectedTo: LocalDate,
)

/** Qué esperados sugiere S23 al registrar un ingreso y cómo se muestra el que el usuario eligió (docs/05, S23). */
object ExpectedIncomeSuggestions {
    /** Los pendientes de la misma cuenta y fuente; el usuario decide con un toque. */
    fun of(
        expectedIncomes: List<ExpectedIncome>,
        sourceNames: Map<IncomeSourceId, String>,
        accountId: AccountId?,
        incomeSourceId: IncomeSourceId?,
    ): List<ExpectedIncomeSuggestion> = expectedIncomes
        .filter { ExpectedIncomeRules.isPending(it.status) && it.accountId == accountId && it.incomeSourceId == incomeSourceId }
        .map { it.toSuggestion(sourceNames) }

    /** El esperado elegido, aunque ya no coincida con la cuenta o la fuente que se tienen ahora. */
    fun selected(
        expectedIncomes: List<ExpectedIncome>,
        sourceNames: Map<IncomeSourceId, String>,
        id: ExpectedIncomeId?,
    ): ExpectedIncomeSuggestion? = expectedIncomes.firstOrNull { it.id == id }?.toSuggestion(sourceNames)

    private fun ExpectedIncome.toSuggestion(sourceNames: Map<IncomeSourceId, String>) =
        ExpectedIncomeSuggestion(id, sourceNames[incomeSourceId].orEmpty(), estimated, range.from, range.to)
}
