package com.al3xiel.my_finances.feature.loans.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.loans.domain.usecase.GrantLoanUseCase
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
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

data class LoanEditorUiState(
    val loaded: Boolean = false,
    val personId: PersonId? = null,
    val accountId: AccountId? = null,
    val amountText: String = "",
    val occurredOn: LocalDate = LocalDate.ofEpochDay(0),
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val note: String = "",
    val personOptions: List<EntityOption> = emptyList(),
    val accountOptions: List<EntityOption> = emptyList(),
    val error: BusinessError? = null,
    val negativeWarning: BusinessError.WouldGoNegative? = null,
    val saved: Boolean = false,
)

sealed interface LoanEditorUiAction {
    data class SelectPerson(val id: PersonId) : LoanEditorUiAction
    data class SelectAccount(val id: AccountId) : LoanEditorUiAction
    data class EditAmount(val text: String) : LoanEditorUiAction
    data class SelectDate(val date: LocalDate) : LoanEditorUiAction
    data class EditNote(val text: String) : LoanEditorUiAction
    data object Save : LoanEditorUiAction
    data object ConfirmNegativeBalance : LoanEditorUiAction
    data object DismissWarning : LoanEditorUiAction
}

/** S36 (nuevo): presta dinero a una persona; el préstamo solo se crea, un error se anula y se vuelve a registrar (D20). */
@HiltViewModel(assistedFactory = LoanEditorViewModel.Factory::class)
class LoanEditorViewModel @AssistedInject constructor(
    @Assisted personIdValue: String?,
    people: PersonRepository,
    private val accounts: AccountRepository,
    private val grantLoan: GrantLoanUseCase,
    clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(personIdValue: String?): LoanEditorViewModel
    }

    private val today = LocalDate.now(clock)

    private data class Form(
        val loaded: Boolean = false,
        val personId: PersonId? = null,
        val accountId: AccountId? = null,
        val amountText: String = "",
        val occurredOn: LocalDate,
        val note: String = "",
        val error: BusinessError? = null,
        val negativeWarning: BusinessError.WouldGoNegative? = null,
        val saved: Boolean = false,
        val submitting: Boolean = false,
    )

    private val form = MutableStateFlow(Form(personId = personIdValue?.let(::PersonId), occurredOn = today))

    val uiState: StateFlow<LoanEditorUiState> = combine(form, people.observeAll(), accounts.observeAccounts()) { form, peopleList, accountList ->
        LoanEditorUiState(
            loaded = form.loaded,
            personId = form.personId,
            accountId = form.accountId,
            amountText = form.amountText,
            occurredOn = form.occurredOn,
            today = today,
            note = form.note,
            personOptions = peopleList.filter { !it.isArchived }.map { EntityOption(it.id.value, it.alias) },
            accountOptions = accountList.filter { !it.account.isArchived }.map { EntityOption(it.account.id.value, it.account.name) },
            error = form.error,
            negativeWarning = form.negativeWarning,
            saved = form.saved,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoanEditorUiState(occurredOn = today, today = today))

    init {
        viewModelScope.launch {
            val first = accounts.observeAccounts().first().firstOrNull { !it.account.isArchived }?.account?.id
            form.update { it.copy(loaded = true, accountId = first) }
        }
    }

    fun onAction(action: LoanEditorUiAction) {
        when (action) {
            is LoanEditorUiAction.SelectPerson -> edit { copy(personId = action.id) }
            is LoanEditorUiAction.SelectAccount -> edit { copy(accountId = action.id) }
            is LoanEditorUiAction.EditAmount -> edit { copy(amountText = MoneyInputFilter.sanitize(action.text)) }
            is LoanEditorUiAction.SelectDate -> edit { copy(occurredOn = action.date) }
            is LoanEditorUiAction.EditNote -> edit { copy(note = action.text) }
            LoanEditorUiAction.Save -> submit(confirmNegative = false)
            LoanEditorUiAction.ConfirmNegativeBalance -> {
                form.update { it.copy(negativeWarning = null) }
                submit(confirmNegative = true)
            }
            LoanEditorUiAction.DismissWarning -> form.update { it.copy(negativeWarning = null) }
        }
    }

    private fun edit(change: Form.() -> Form) = form.update { it.change().copy(error = null) }

    private fun submit(confirmNegative: Boolean) {
        val current = form.value
        if (current.submitting) return
        val person = current.personId ?: return fail(BusinessError.PersonRequired)
        val amount = MoneyInputFilter.toMinor(current.amountText)?.let { Money(it, CurrencyCode.PEN) } ?: return fail(BusinessError.InvalidAmount)
        val account = current.accountId ?: return fail(BusinessError.NotFound)
        form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val note = current.note.trim().ifEmpty { null }
            val outcome = attempt { grantLoan(person, account, amount, note, current.occurredOn, confirmNegative) }
            when (val error = outcome.errorOrNull()) {
                null -> form.update { it.copy(saved = true, submitting = false) }
                is BusinessError.WouldGoNegative -> form.update { it.copy(negativeWarning = error, submitting = false) }
                else -> form.update { it.copy(error = error, submitting = false) }
            }
        }
    }

    private fun fail(error: BusinessError) = form.update { it.copy(error = error) }
}
