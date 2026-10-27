package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import java.time.LocalDate
import javax.inject.Inject

class RegisterExpenseUseCase @Inject constructor(
    private val rules: MovementRules,
    private val accounts: AccountRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    /** Si el saldo queda negativo avisa con `WouldGoNegative`; repetir con `confirmNegativeBalance` lo guarda (D14). */
    suspend operator fun invoke(
        accountId: AccountId,
        total: Money,
        parts: List<ExpensePartInput>,
        note: String?,
        occurredOn: LocalDate,
        confirmNegativeBalance: Boolean = false,
    ): Outcome<MovementId> {
        rules.checkDate(occurredOn)?.let { return Outcome.Failed(it) }
        rules.checkAccount(accountId)?.let { return Outcome.Failed(it) }
        rules.checkExpense(total, parts)?.let { return Outcome.Failed(it) }
        if (!confirmNegativeBalance) {
            val balance = accounts.balanceMinor(accountId)
            rules.negativeBalanceWarning(balance, balance - total.amountMinor)?.let { return Outcome.Failed(it) }
        }

        val id = MovementId(ids.newId())
        val data = parts.map { ExpensePartData(PartId(ids.newId()), it.amount, it.categoryId, it.tagIds, null, null) }
        recorder.record(ExpenseRegistered(id, accountId, total, data, null, note), occurredOn)
        return Outcome.Ok(id)
    }
}
