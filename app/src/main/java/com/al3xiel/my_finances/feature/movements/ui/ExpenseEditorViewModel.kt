package com.al3xiel.my_finances.feature.movements.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.model.Movement
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** S22: registra un gasto nuevo, o corrige uno existente (con motivo) si recibe su id. */
@HiltViewModel(assistedFactory = ExpenseEditorViewModel.Factory::class)
class ExpenseEditorViewModel @AssistedInject constructor(
    @Assisted movementIdValue: String?,
    private val movements: MovementRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    tags: TagRepository,
    private val registerExpense: RegisterExpenseUseCase,
    private val correctMovement: CorrectMovementUseCase,
    clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(movementIdValue: String?): ExpenseEditorViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    private val movementId = movementIdValue?.let(::MovementId)
    private val today = LocalDate.now(clock)

    private data class Form(
        val loaded: Boolean = false,
        val notFound: Boolean = false,
        val totalText: String = "",
        val accountId: AccountId? = null,
        val occurredOn: LocalDate,
        val note: String = "",
        val parts: List<PartDraft> = emptyList(),
        val nextKey: Int = 0,
        val error: BusinessError? = null,
        val negativeWarning: BusinessError.WouldGoNegative? = null,
        val showReasonDialog: Boolean = false,
        val pendingReason: String? = null,
        val saved: Boolean = false,
        val submitting: Boolean = false,
    )

    private val form = MutableStateFlow(Form(occurredOn = today))

    val uiState: StateFlow<ExpenseEditorUiState> = combine(
        form,
        accounts.observeAccounts(),
        categories.observeAll(),
        tags.observeAll(),
    ) { form, accountList, categoryList, tagList ->
        ExpenseEditorUiState(
            isCorrection = movementId != null,
            loaded = form.loaded,
            notFound = form.notFound,
            totalText = form.totalText,
            accountId = form.accountId,
            occurredOn = form.occurredOn,
            today = today,
            note = form.note,
            parts = form.parts,
            accountOptions = accountList.filter { !it.account.isArchived }.map { EntityOption(it.account.id.value, it.account.name) },
            categoryOptions = categoryOptions(categoryList),
            tagOptions = tagList.filter { !it.isArchived }.map { EntityOption(it.id.value, it.name) },
            unassignedMinor = unassigned(form),
            error = form.error,
            negativeWarning = form.negativeWarning,
            showReasonDialog = form.showReasonDialog,
            saved = form.saved,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ExpenseEditorUiState(occurredOn = today, today = today))

    init {
        viewModelScope.launch { initialize() }
    }

    private suspend fun initialize() {
        if (movementId == null) {
            val last = movements.lastExpense()
            val account = last?.accountId ?: accounts.observeAccounts().first().firstOrNull { !it.account.isArchived }?.account?.id
            val category = last?.parts?.firstOrNull { it.kind == PartKind.NORMAL }?.categoryId
                ?: categories.observeAll().first().firstOrNull { !it.isArchived }?.id
            form.update { it.copy(loaded = true, accountId = account, parts = listOf(PartDraft(key = 0, categoryId = category)), nextKey = 1) }
            return
        }
        val movement = movements.find(movementId)
        if (movement == null || movement.type != MovementType.EXPENSE || movement.status != MovementStatus.ACTIVE) {
            form.update { it.copy(loaded = true, notFound = true) }
            return
        }
        form.update { loaded(it, movement) }
    }

    private fun loaded(current: Form, movement: Movement): Form {
        val parts = movement.parts.filter { it.kind == PartKind.NORMAL }
        return current.copy(
            loaded = true,
            totalText = minorToText(movement.amount.amountMinor),
            accountId = movement.accountId,
            occurredOn = movement.occurredOn,
            note = movement.note.orEmpty(),
            parts = parts.mapIndexed { i, part ->
                PartDraft(i, minorToText(part.amount.amountMinor), part.categoryId, part.tagIds.toSet(), part.id)
            },
            nextKey = parts.size,
        )
    }

    fun onAction(action: ExpenseEditorUiAction) {
        when (action) {
            is ExpenseEditorUiAction.EditTotal -> edit { copy(totalText = MoneyInputFilter.sanitize(action.text)) }
            is ExpenseEditorUiAction.SelectAccount -> edit { copy(accountId = action.id) }
            is ExpenseEditorUiAction.SelectOccurredOn -> edit { copy(occurredOn = action.occurredOn) }
            is ExpenseEditorUiAction.EditNote -> edit { copy(note = action.text) }
            is ExpenseEditorUiAction.EditPartAmount ->
                editPart(action.key) { copy(amountText = MoneyInputFilter.sanitize(action.text)) }
            is ExpenseEditorUiAction.SelectPartCategory -> editPart(action.key) { copy(categoryId = action.id) }
            is ExpenseEditorUiAction.TogglePartTag -> editPart(action.key) {
                copy(tagIds = if (action.id in tagIds) tagIds - action.id else tagIds + action.id)
            }
            ExpenseEditorUiAction.AddPart -> edit {
                // Al pasar de una a dos partes, la primera conserva el total para no perder lo tecleado.
                val seeded = if (parts.size == 1) listOf(parts.single().copy(amountText = totalText)) else parts
                copy(parts = seeded + PartDraft(key = nextKey, categoryId = parts.lastOrNull()?.categoryId), nextKey = nextKey + 1)
            }
            is ExpenseEditorUiAction.RemovePart -> edit {
                if (parts.size > 1) copy(parts = parts.filterNot { it.key == action.key }) else this
            }
            ExpenseEditorUiAction.Save -> save()
            is ExpenseEditorUiAction.ConfirmReason -> {
                form.update { it.copy(pendingReason = action.reason, showReasonDialog = false) }
                submit(confirmNegative = false)
            }
            ExpenseEditorUiAction.DismissReason -> form.update { it.copy(showReasonDialog = false) }
            ExpenseEditorUiAction.ConfirmNegativeBalance -> {
                form.update { it.copy(negativeWarning = null) }
                submit(confirmNegative = true)
            }
            ExpenseEditorUiAction.DismissWarning -> form.update { it.copy(negativeWarning = null, pendingReason = null) }
        }
    }

    /** Cualquier cambio del usuario descarta el error anterior. */
    private fun edit(change: Form.() -> Form) = form.update { it.change().copy(error = null) }

    private fun editPart(key: Int, change: PartDraft.() -> PartDraft) =
        edit { copy(parts = parts.map { if (it.key == key) it.change() else it }) }

    private fun save() {
        if (movementId != null && form.value.pendingReason == null) {
            form.update { it.copy(showReasonDialog = true) }
        } else {
            submit(confirmNegative = false)
        }
    }

    private fun submit(confirmNegative: Boolean) {
        val current = form.value
        if (current.submitting) return
        val total = MoneyInputFilter.toMinor(current.totalText)?.let { Money(it, CurrencyCode.PEN) }
        val account = current.accountId
        val inputs = partInputs(current, total)
        if (total == null) return fail(BusinessError.InvalidAmount)
        if (account == null || inputs == null) return fail(BusinessError.NotFound)

        form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val note = current.note.trim().ifEmpty { null }
            val outcome = attempt {
                if (movementId == null) {
                    registerExpense(account, total, inputs, note, current.occurredOn, confirmNegative)
                } else {
                    correctMovement(
                        movementId,
                        MovementDraft.Expense(account, total, inputs, note, current.occurredOn),
                        current.pendingReason.orEmpty(),
                        confirmNegative,
                    )
                }
            }
            when (val error = outcome.errorOrNull()) {
                null -> form.update { it.copy(saved = true, submitting = false) }
                is BusinessError.WouldGoNegative -> form.update { it.copy(negativeWarning = error, submitting = false) }
                else -> form.update { it.copy(error = error, pendingReason = null, submitting = false) }
            }
        }
    }

    private fun fail(error: BusinessError) = form.update { it.copy(error = error, pendingReason = null) }

    private fun partInputs(form: Form, total: Money?): List<ExpensePartInput>? {
        val single = form.parts.size == 1
        return form.parts.map { part ->
            val category = part.categoryId ?: return null
            val minor = if (single) total?.amountMinor ?: 0L else MoneyInputFilter.toMinor(part.amountText) ?: 0L
            ExpensePartInput(part.partId, Money(minor, CurrencyCode.PEN), category, part.tagIds.toList())
        }
    }

    private fun unassigned(form: Form): Long {
        if (form.parts.size < 2) return 0
        val total = MoneyInputFilter.toMinor(form.totalText) ?: 0L
        return total - form.parts.sumOf { MoneyInputFilter.toMinor(it.amountText) ?: 0L }
    }

    private fun categoryOptions(all: List<Category>): List<EntityOption> {
        val active = all.filter { !it.isArchived }
        val names = active.associate { it.id to it.name }
        return active.map { category ->
            val parent = category.parentId?.let(names::get)
            EntityOption(category.id.value, if (parent != null) "$parent › ${category.name}" else category.name)
        }
    }

    /** `1250` céntimos → `"12.5"`, el mismo texto que el usuario habría tecleado. */
    private fun minorToText(minor: Long): String {
        val cents = minor % 100
        return if (cents == 0L) (minor / 100).toString() else "${minor / 100}.${cents.toString().padStart(2, '0').trimEnd('0')}"
    }
}
