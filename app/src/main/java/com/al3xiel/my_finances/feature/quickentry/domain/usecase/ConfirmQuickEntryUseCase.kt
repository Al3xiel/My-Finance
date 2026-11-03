package com.al3xiel.my_finances.feature.quickentry.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.rules.PoolDefaults
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.quickentry.domain.model.QuickEntryDraft
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Guarda la vista previa con los mismos casos de uso que los editores; no tiene reglas propias (D66). */
class ConfirmQuickEntryUseCase @Inject constructor(
    private val registerExpense: RegisterExpenseUseCase,
    private val registerIncome: RegisterIncomeUseCase,
    private val pools: PoolRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(draft: QuickEntryDraft, confirmNegativeBalance: Boolean = false): Outcome<MovementId> {
        val today = LocalDate.now(clock)
        return when (draft) {
            is QuickEntryDraft.Income ->
                registerIncome(draft.accountId, draft.amount, draft.incomeSourceId, draft.note, today)
            is QuickEntryDraft.Expense -> {
                val pool = draft.poolId?.let { pools.find(it) ?: return Outcome.Failed(BusinessError.NotFound) }
                val tags = pool?.let { PoolDefaults.tagsFor(it, draft.tagIds) } ?: draft.tagIds
                val part = ExpensePartInput(null, draft.amount, draft.categoryId, tags.toList(), draft.poolId, draft.fundId)
                registerExpense(draft.accountId, draft.amount, listOf(part), draft.note, today, confirmNegativeBalance)
            }
        }
    }
}
