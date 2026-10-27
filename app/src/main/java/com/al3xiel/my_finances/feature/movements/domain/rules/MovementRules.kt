package com.al3xiel.my_finances.feature.movements.domain.rules

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Referencias que cada parte ya tenía antes de corregir el gasto: aunque hoy estén archivadas se aceptan en esa
 * misma parte (corregir una nota no debe obligar a cambiar la categoría), pero no en otra parte ni en una nueva.
 */
data class UnchangedReferences(
    val categories: Map<PartId, CategoryId> = emptyMap(),
    val tags: Map<PartId, Set<TagId>> = emptyMap(),
)

/** Validaciones de `docs/02 §5` compartidas por registrar y corregir. Cada una devuelve el primer error o `null`. */
class MovementRules @Inject constructor(
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
    private val incomeSources: IncomeSourceRepository,
    private val clock: Clock,
) {
    fun checkMoney(money: Money): BusinessError? = when {
        money.currency != CurrencyCode.PEN -> BusinessError.UnsupportedCurrency
        money.amountMinor <= 0 || money.amountMinor > MAX_AMOUNT_MINOR -> BusinessError.InvalidAmount
        else -> null
    }

    fun checkDate(occurredOn: LocalDate): BusinessError? =
        if (occurredOn.isAfter(LocalDate.now(clock))) BusinessError.FutureDate else null

    suspend fun checkAccount(id: AccountId, allowArchived: Boolean = false): BusinessError? {
        val account = accounts.find(id) ?: return BusinessError.NotFound
        return if (account.isArchived && !allowArchived) BusinessError.AlreadyArchived else null
    }

    suspend fun checkCategory(id: CategoryId, allowArchived: Boolean = false): BusinessError? {
        val category = categories.find(id) ?: return BusinessError.NotFound
        return if (category.isArchived && !allowArchived) BusinessError.AlreadyArchived else null
    }

    suspend fun checkIncomeSource(id: IncomeSourceId): BusinessError? =
        if (incomeSources.find(id) == null) BusinessError.NotFound else null

    /** Gasto: ≥ 1 parte, cada parte con monto y categoría válidos, conceptos existentes y Σ partes = total. */
    suspend fun checkExpense(
        total: Money,
        parts: List<ExpensePartInput>,
        unchanged: UnchangedReferences = UnchangedReferences(),
    ): BusinessError? {
        checkMoney(total)?.let { return it }
        if (parts.isEmpty()) return BusinessError.NoParts
        for (part in parts) {
            checkMoney(part.amount)?.let { return it }
            checkCategory(part.categoryId, allowArchived = part.partId != null && unchanged.categories[part.partId] == part.categoryId)?.let { return it }
            for (tagId in part.tagIds) {
                val tag = tags.find(tagId) ?: return BusinessError.NotFound
                if (tag.isArchived && !(part.partId != null && tagId in unchanged.tags[part.partId].orEmpty())) return BusinessError.AlreadyArchived
            }
        }
        val sum = try {
            parts.fold(0L) { acc, part -> Math.addExact(acc, part.amount.amountMinor) }
        } catch (_: ArithmeticException) {
            return BusinessError.PartsMismatch
        }
        if (sum != total.amountMinor) return BusinessError.PartsMismatch
        return null
    }

    /** Aviso solo si el saldo queda negativo y la operación lo empeora (D14). */
    fun negativeBalanceWarning(currentBalance: Long, resultingBalance: Long): BusinessError? =
        if (resultingBalance < 0 && resultingBalance < currentBalance) BusinessError.WouldGoNegative(resultingBalance) else null

    companion object {
        /** S/ 999,999,999.99: lo que cabe en el campo de monto y deja margen para sumar sin desbordar un `Long`. */
        const val MAX_AMOUNT_MINOR = 99_999_999_999L
    }
}
