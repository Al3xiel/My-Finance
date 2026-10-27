package com.al3xiel.my_finances.data.projection.loan

import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.movement.LedgerEntryEntity
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementEntity
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.LoanGranted
import com.al3xiel.my_finances.domain.model.event.LoanRepaymentReceived
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import javax.inject.Inject

/**
 * Deudas sueltas: el préstamo es un movimiento `LOAN_DISBURSEMENT` con asiento `LOAN_OUT` (no es gasto) y cada pago, un
 * `LOAN_REPAYMENT` con `LOAN_IN` (no es ingreso). Lo pagado y el estado salen de los pagos que siguen vigentes; anular un
 * pago lo borra y recalcula (D20).
 */
class LoanProjector @Inject constructor(
    private val loans: LoanDao,
    private val poster: LedgerPoster,
) : EventProjector {
    override val name = "loan"
    override val version = 1
    override val order = 20

    private val repaymentSourceId = SystemCatalog.incomeSources.first { it.kind == IncomeSourceKind.LOAN_REPAYMENT }.id.value

    override suspend fun apply(event: StoredEvent) {
        when (val e = event.event) {
            is LoanGranted -> grant(event, e)
            is LoanRepaymentReceived -> receive(event, e)
            is MovementVoided -> void(event, e)
            else -> Unit
        }
    }

    override suspend fun reset() {
        loans.clearRepayments()
        loans.clearLoans()
        loans.deleteOwnEntries()
        loans.deleteOwnMovements()
    }

    private suspend fun grant(event: StoredEvent, e: LoanGranted) {
        val occurredOn = event.occurredOn.toString()
        poster.post(
            movement(event, e.movementId.value, MovementType.LOAN_DISBURSEMENT, e.accountId.value, e.amount.amountMinor, e.amount.currency, e.personId.value, null, e.note),
            entry(event, e.movementId.value, e.accountId.value, -e.amount.amountMinor, LedgerEntryKind.LOAN_OUT),
        )
        loans.upsertLoan(
            LoanEntity(e.loanId.value, e.personId.value, e.movementId.value, e.amount.amountMinor, 0, LoanStatus.OPEN, occurredOn),
        )
    }

    private suspend fun receive(event: StoredEvent, e: LoanRepaymentReceived) {
        val loan = loans.loan(e.loanId.value) ?: return
        poster.post(
            movement(event, e.movementId.value, MovementType.LOAN_REPAYMENT, e.accountId.value, e.amount.amountMinor, e.amount.currency, loan.personId, repaymentSourceId, null),
            entry(event, e.movementId.value, e.accountId.value, e.amount.amountMinor, LedgerEntryKind.LOAN_IN),
        )
        loans.insertRepayment(
            LoanRepaymentEntity(e.repaymentId.value, loan.id, e.movementId.value, e.amount.amountMinor, event.occurredOn.toString()),
        )
        refresh(loan.id)
    }

    private suspend fun void(event: StoredEvent, e: MovementVoided) {
        val id = e.movementId.value
        loans.repaymentByMovement(id)?.let { repayment ->
            if (!poster.void(id, event.sequence)) return
            loans.deleteRepayment(repayment.id)
            refresh(repayment.loanId)
            return
        }
        if (loans.loanByMovement(id) != null) poster.void(id, event.sequence)
    }

    private suspend fun refresh(loanId: String) {
        val loan = loans.loan(loanId) ?: return
        val repaid = loans.repaidOf(loanId)
        val status = if (repaid >= loan.principalMinor) LoanStatus.REPAID else LoanStatus.OPEN
        loans.upsertLoan(loan.copy(repaidMinor = repaid, status = status))
    }

    private fun movement(
        event: StoredEvent,
        id: String,
        type: MovementType,
        accountId: String,
        amountMinor: Long,
        currency: com.al3xiel.my_finances.core.money.CurrencyCode,
        personId: String,
        incomeSourceId: String?,
        note: String?,
    ) = MovementEntity(
        id = id,
        type = type,
        accountId = accountId,
        counterAccountId = null,
        amountMinor = amountMinor,
        currency = currency,
        incomeSourceId = incomeSourceId,
        personId = personId,
        note = note,
        occurredOn = event.occurredOn.toString(),
        status = MovementStatus.ACTIVE,
        revision = 1,
        lastSequence = event.sequence,
    )

    private fun entry(event: StoredEvent, movementId: String, accountId: String, deltaMinor: Long, kind: LedgerEntryKind) =
        LedgerEntryEntity(
            id = "${event.sequence}-0",
            sequence = event.sequence,
            movementId = movementId,
            accountId = accountId,
            deltaMinor = deltaMinor,
            entryKind = kind,
            occurredOn = event.occurredOn.toString(),
            isVoided = false,
        )
}
