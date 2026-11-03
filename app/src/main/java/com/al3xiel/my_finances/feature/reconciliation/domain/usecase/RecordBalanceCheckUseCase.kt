package com.al3xiel.my_finances.feature.reconciliation.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import javax.inject.Inject

/**
 * Paso 1 de la conciliación: guarda el saldo real del banco y, como evidencia, el que calculaba la app. Nunca mueve saldos
 * (C1). El saldo real puede ser 0 o negativo (D34), así que no pasa por la validación de montos positivos.
 */
class RecordBalanceCheckUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val reconciliations: ReconciliationRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(accountId: AccountId, realBalance: Money): Outcome<ReconciliationId> {
        val account = accounts.find(accountId) ?: return Outcome.Failed(BusinessError.NotFound)
        if (account.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        if (realBalance.currency != account.currency) return Outcome.Failed(BusinessError.UnsupportedCurrency)
        if (reconciliations.openFor(accountId) != null) return Outcome.Failed(BusinessError.ReconciliationAlreadyOpen)

        val id = ReconciliationId(ids.newId())
        val computed = Money(accounts.balanceMinor(accountId), account.currency)
        recorder.record(BalanceCheckRecorded(id, accountId, realBalance, computed))
        return Outcome.Ok(id)
    }
}
