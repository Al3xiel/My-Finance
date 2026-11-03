package com.al3xiel.my_finances.data.projection.expectedincome

import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeCancelled
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeConfirmed
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeEdited
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeScheduled
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import javax.inject.Inject

/**
 * Ingresos esperados. Se explica solo con sus eventos y su tabla: nunca lee `movements` (D27), así que `OVERDUE` y
 * "vuelve a pendiente" no existen aquí. La edición cambia solo los campos presentes; los casos de uso ya rechazan
 * editar o confirmar lo que no corresponde, y un evento de un esperado inexistente se ignora para que el replay dé lo
 * mismo que la proyección en vivo.
 */
class ExpectedIncomeProjector @Inject constructor(
    private val expectedIncomes: ExpectedIncomeDao,
) : EventProjector {
    override val name = "expected_income"
    override val version = 1

    override suspend fun apply(event: StoredEvent) {
        when (val e = event.event) {
            is ExpectedIncomeScheduled -> expectedIncomes.upsert(
                ExpectedIncomeEntity(
                    e.expectedIncomeId.value, e.incomeSourceId.value, e.accountId.value, e.estimatedAmount.amountMinor,
                    e.expectedFrom.toString(), e.expectedTo.toString(), e.repeatsMonthly, ExpectedIncomeStatus.SCHEDULED,
                    cancelReason = null, confirmedMovementId = null, nextExpectedId = null,
                ),
            )

            is ExpectedIncomeEdited -> expectedIncomes.expected(e.expectedIncomeId.value)?.let {
                expectedIncomes.upsert(
                    it.copy(
                        incomeSourceId = e.incomeSourceId?.value ?: it.incomeSourceId,
                        accountId = e.accountId?.value ?: it.accountId,
                        estimatedMinor = e.estimatedAmount?.amountMinor ?: it.estimatedMinor,
                        expectedFrom = e.expectedFrom?.toString() ?: it.expectedFrom,
                        expectedTo = e.expectedTo?.toString() ?: it.expectedTo,
                        repeatsMonthly = e.repeatsMonthly ?: it.repeatsMonthly,
                    ),
                )
            }

            is ExpectedIncomeConfirmed -> expectedIncomes.expected(e.expectedIncomeId.value)?.let {
                expectedIncomes.upsert(
                    it.copy(
                        status = ExpectedIncomeStatus.CONFIRMED,
                        confirmedMovementId = e.incomeMovementId.value,
                        nextExpectedId = e.nextExpectedIncomeId?.value,
                    ),
                )
            }

            is ExpectedIncomeCancelled -> expectedIncomes.expected(e.expectedIncomeId.value)?.let {
                expectedIncomes.upsert(it.copy(status = ExpectedIncomeStatus.CANCELLED, cancelReason = e.reason))
            }

            else -> Unit
        }
    }

    override suspend fun reset() = expectedIncomes.clear()
}
