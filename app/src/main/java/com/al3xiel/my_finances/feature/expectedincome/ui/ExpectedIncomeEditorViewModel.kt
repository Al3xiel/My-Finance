package com.al3xiel.my_finances.feature.expectedincome.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeRules
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.EditExpectedIncomeUseCase
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.ExpectedIncomeChanges
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.ScheduleExpectedIncomeUseCase
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

data class ExpectedIncomeEditorUiState(
    val loaded: Boolean = false,
    val isEditing: Boolean = false,
    val notFound: Boolean = false,
    /** Ya se confirmó o se canceló: solo uno pendiente se corrige. */
    val notEditable: Boolean = false,
    val amountText: String = "",
    val accountId: AccountId? = null,
    val sourceId: IncomeSourceId? = null,
    val expectedFrom: LocalDate = LocalDate.ofEpochDay(0),
    val expectedTo: LocalDate = LocalDate.ofEpochDay(0),
    val repeatsMonthly: Boolean = false,
    val accountOptions: List<EntityOption> = emptyList(),
    val sourceOptions: List<EntityOption> = emptyList(),
    val error: BusinessError? = null,
    val saved: Boolean = false,
)

sealed interface ExpectedIncomeEditorUiAction {
    data class EditAmount(val text: String) : ExpectedIncomeEditorUiAction
    data class SelectAccount(val id: AccountId) : ExpectedIncomeEditorUiAction
    data class SelectSource(val id: IncomeSourceId) : ExpectedIncomeEditorUiAction
    data class SetFrom(val day: LocalDate) : ExpectedIncomeEditorUiAction
    data class SetTo(val day: LocalDate) : ExpectedIncomeEditorUiAction
    data class SetRepeatsMonthly(val repeats: Boolean) : ExpectedIncomeEditorUiAction
    data object Save : ExpectedIncomeEditorUiAction
}

/** Editor de un ingreso esperado: programa uno nuevo o corrige uno pendiente. El rango puede estar en el pasado. */
@HiltViewModel(assistedFactory = ExpectedIncomeEditorViewModel.Factory::class)
class ExpectedIncomeEditorViewModel @AssistedInject constructor(
    @Assisted expectedIncomeIdValue: String?,
    private val expectedIncomes: ExpectedIncomeRepository,
    private val accounts: AccountRepository,
    private val incomeSources: IncomeSourceRepository,
    private val scheduleExpectedIncome: ScheduleExpectedIncomeUseCase,
    private val editExpectedIncome: EditExpectedIncomeUseCase,
    clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(expectedIncomeIdValue: String?): ExpectedIncomeEditorViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    private val expectedIncomeId = expectedIncomeIdValue?.let(::ExpectedIncomeId)
    private val today = LocalDate.now(clock)

    private data class Form(
        val loaded: Boolean = false,
        val notFound: Boolean = false,
        val notEditable: Boolean = false,
        val amountText: String = "",
        val accountId: AccountId? = null,
        val sourceId: IncomeSourceId? = null,
        val expectedFrom: LocalDate,
        val expectedTo: LocalDate,
        val repeatsMonthly: Boolean = false,
        val error: BusinessError? = null,
        val saved: Boolean = false,
    )

    private val form = MutableStateFlow(Form(expectedFrom = today, expectedTo = today.plusDays(DEFAULT_RANGE_DAYS)))
    private var submitting = false

    val uiState: StateFlow<ExpectedIncomeEditorUiState> = combine(form, accounts.observeAccounts(), incomeSources.observeAll()) { form, accountList, sourceList ->
        ExpectedIncomeEditorUiState(
            loaded = form.loaded,
            isEditing = expectedIncomeId != null,
            notFound = form.notFound,
            notEditable = form.notEditable,
            amountText = form.amountText,
            accountId = form.accountId,
            sourceId = form.sourceId,
            expectedFrom = form.expectedFrom,
            expectedTo = form.expectedTo,
            repeatsMonthly = form.repeatsMonthly,
            // La cuenta o fuente actual se ve aunque esté archivada, para que se pueda cambiar por otra.
            accountOptions = accountList.filter { !it.account.isArchived || it.account.id == form.accountId }
                .map { EntityOption(it.account.id.value, it.account.name) },
            sourceOptions = sourceList.filter { it.kind == IncomeSourceKind.REGULAR || it.id == form.sourceId }
                .map { EntityOption(it.id.value, it.name) },
            error = form.error,
            saved = form.saved,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ExpectedIncomeEditorUiState(expectedFrom = today, expectedTo = today.plusDays(DEFAULT_RANGE_DAYS)),
    )

    init {
        viewModelScope.launch { initialize() }
    }

    private suspend fun initialize() {
        val id = expectedIncomeId
        if (id == null) {
            val account = accounts.observeAccounts().first().firstOrNull { !it.account.isArchived }?.account?.id
            val source = incomeSources.observeAll().first().firstOrNull { it.kind == IncomeSourceKind.REGULAR }?.id
            form.update { it.copy(loaded = true, accountId = account, sourceId = source) }
            return
        }
        val current = expectedIncomes.find(id)
        when {
            current == null -> form.update { it.copy(loaded = true, notFound = true) }
            !ExpectedIncomeRules.isPending(current.status) -> form.update { it.copy(loaded = true, notEditable = true) }
            else -> form.update {
                it.copy(
                    loaded = true,
                    amountText = MoneyInputFilter.fromMinor(current.estimated.amountMinor),
                    accountId = current.accountId,
                    sourceId = current.incomeSourceId,
                    expectedFrom = current.range.from,
                    expectedTo = current.range.to,
                    repeatsMonthly = current.repeatsMonthly,
                )
            }
        }
    }

    fun onAction(action: ExpectedIncomeEditorUiAction) {
        when (action) {
            is ExpectedIncomeEditorUiAction.EditAmount -> edit { copy(amountText = MoneyInputFilter.sanitize(action.text)) }
            is ExpectedIncomeEditorUiAction.SelectAccount -> edit { copy(accountId = action.id) }
            is ExpectedIncomeEditorUiAction.SelectSource -> edit { copy(sourceId = action.id) }
            is ExpectedIncomeEditorUiAction.SetFrom -> edit { copy(expectedFrom = action.day) }
            is ExpectedIncomeEditorUiAction.SetTo -> edit { copy(expectedTo = action.day) }
            is ExpectedIncomeEditorUiAction.SetRepeatsMonthly -> edit { copy(repeatsMonthly = action.repeats) }
            ExpectedIncomeEditorUiAction.Save -> save()
        }
    }

    private fun edit(change: Form.() -> Form) = form.update { it.change().copy(error = null) }

    private fun save() {
        val current = form.value
        if (submitting) return
        val account = current.accountId
        val source = current.sourceId
        if (account == null || source == null) {
            form.update { it.copy(error = BusinessError.NotFound) }
            return
        }
        submitting = true
        // Un monto vacío o ilegible llega como 0 y las reglas lo rechazan como monto inválido.
        val estimated = Money(MoneyInputFilter.toMinor(current.amountText) ?: 0, CurrencyCode.PEN)
        viewModelScope.launch {
            val id = expectedIncomeId
            val error = if (id != null) {
                val changes = ExpectedIncomeChanges(source, account, estimated, current.expectedFrom, current.expectedTo, current.repeatsMonthly)
                attempt { editExpectedIncome(id, changes) }.errorOrNull()
            } else {
                attempt {
                    scheduleExpectedIncome(account, source, estimated, ExpectedRange(current.expectedFrom, current.expectedTo), current.repeatsMonthly)
                }.errorOrNull()
            }
            submitting = false
            form.update { if (error == null) it.copy(saved = true) else it.copy(error = error) }
        }
    }

    private companion object {
        const val DEFAULT_RANGE_DAYS = 7L
    }
}
