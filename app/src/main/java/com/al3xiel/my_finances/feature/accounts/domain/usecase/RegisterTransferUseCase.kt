package com.al3xiel.my_finances.feature.accounts.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.FeeData
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import java.time.LocalDate
import javax.inject.Inject

class RegisterTransferUseCase @Inject constructor(
    private val rules: MovementRules,
    private val accounts: AccountRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    /** El aviso de saldo negativo cuenta el monto más la comisión, que sale de la cuenta origen. */
    suspend operator fun invoke(
        from: AccountId,
        to: AccountId,
        amount: Money,
        fee: FeeData?,
        note: String?,
        occurredOn: LocalDate,
        confirmNegativeBalance: Boolean = false,
    ): Outcome<MovementId> {
        if (from == to) return Outcome.Failed(BusinessError.SameAccount)
        rules.checkMoney(amount)?.let { return Outcome.Failed(it) }
        if (fee != null) {
            rules.checkMoney(fee.amount)?.let { return Outcome.Failed(it) }
            rules.checkCategory(fee.categoryId)?.let { return Outcome.Failed(it) }
        }
        rules.checkDate(occurredOn)?.let { return Outcome.Failed(it) }
        rules.checkAccount(from)?.let { return Outcome.Failed(it) }
        rules.checkAccount(to)?.let { return Outcome.Failed(it) }
        if (!confirmNegativeBalance) {
            val balance = accounts.balanceMinor(from)
            val outflow = Math.addExact(amount.amountMinor, fee?.amount?.amountMinor ?: 0L)
            rules.negativeBalanceWarning(balance, balance - outflow)?.let { return Outcome.Failed(it) }
        }

        val id = MovementId(ids.newId())
        recorder.record(TransferRegistered(id, from, to, amount, fee, null, note), occurredOn)
        return Outcome.Ok(id)
    }
}
