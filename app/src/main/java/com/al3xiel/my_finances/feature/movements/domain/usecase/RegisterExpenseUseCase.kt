package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FundReferenceGuard
import com.al3xiel.my_finances.domain.usecase.PoolReferenceGuard
import com.al3xiel.my_finances.domain.usecase.ReconciliationLinkGuard
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import java.time.LocalDate
import javax.inject.Inject

class RegisterExpenseUseCase @Inject constructor(
    private val rules: MovementRules,
    private val accounts: AccountRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val lock: MovementWriteLock,
    private val poolGuards: Set<@JvmSuppressWildcards PoolReferenceGuard> = emptySet(),
    private val fundGuards: Set<@JvmSuppressWildcards FundReferenceGuard> = emptySet(),
    private val linkGuards: Set<@JvmSuppressWildcards ReconciliationLinkGuard> = emptySet(),
) {
    /** Si el saldo queda negativo avisa con `WouldGoNegative`; repetir con `confirmNegativeBalance` lo guarda (D14). */
    suspend operator fun invoke(
        accountId: AccountId,
        total: Money,
        parts: List<ExpensePartInput>,
        note: String?,
        occurredOn: LocalDate,
        confirmNegativeBalance: Boolean = false,
        reconciliationId: ReconciliationId? = null,
    ): Outcome<MovementId> =
        lock.withLock { execute(accountId, total, parts, note, occurredOn, confirmNegativeBalance, reconciliationId) }

    private suspend fun execute(
        accountId: AccountId,
        total: Money,
        parts: List<ExpensePartInput>,
        note: String?,
        occurredOn: LocalDate,
        confirmNegativeBalance: Boolean,
        reconciliationId: ReconciliationId?,
    ): Outcome<MovementId> {
        rules.checkDate(occurredOn)?.let { return Outcome.Failed(it) }
        rules.checkAccount(accountId)?.let { return Outcome.Failed(it) }
        rules.checkExpense(total, parts)?.let { return Outcome.Failed(it) }
        reconciliationId?.let { rid -> linkGuards.firstNotNullOfOrNull { it.check(rid, setOf(accountId)) }?.let { return Outcome.Failed(it) } }
        parts.mapNotNull { it.poolId }.forEach { pool ->
            poolGuards.firstNotNullOfOrNull { it.check(pool, unchanged = false) }?.let { return Outcome.Failed(it) }
        }
        parts.mapNotNull { it.fundId }.distinct().forEach { fund ->
            fundGuards.firstNotNullOfOrNull { it.check(fund, occurredOn, unchanged = false) }?.let { return Outcome.Failed(it) }
        }
        if (!confirmNegativeBalance) {
            val balance = accounts.balanceMinor(accountId)
            rules.negativeBalanceWarning(balance, balance - total.amountMinor)?.let { return Outcome.Failed(it) }
        }

        val id = MovementId(ids.newId())
        val data = parts.map { ExpensePartData(PartId(ids.newId()), it.amount, it.categoryId, it.tagIds, it.poolId, it.fundId) }
        recorder.record(ExpenseRegistered(id, accountId, total, data, reconciliationId, note), occurredOn)
        return Outcome.Ok(id)
    }
}
