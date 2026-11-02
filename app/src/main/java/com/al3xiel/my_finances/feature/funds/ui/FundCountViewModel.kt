package com.al3xiel.my_finances.feature.funds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.rules.FundRules
import com.al3xiel.my_finances.feature.funds.domain.usecase.CountFundUseCase
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

data class FundCountUiState(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val overview: FundOverview? = null,
    val declaredText: String = "",
    val accountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val accountOptions: List<EntityOption> = emptyList(),
    val categoryOptions: List<EntityOption> = emptyList(),
    /** Lo que sale de lo declarado; `null` mientras no hay un monto válido o si se rechazaría. */
    val realSpent: Money? = null,
    val estimatedSpent: Money? = null,
    val untracked: Money? = null,
    /** El arqueo se rechazaría tal como está (declaras más de lo que le queda al fondo). */
    val rejection: BusinessError? = null,
    /** El fondo ya terminó: conviene arquear antes de cerrarlo. */
    val suggestBeforeClose: Boolean = false,
    val error: BusinessError? = null,
    val saved: Boolean = false,
)

sealed interface FundCountUiAction {
    data class EditDeclared(val text: String) : FundCountUiAction
    data class SelectAccount(val id: AccountId) : FundCountUiAction
    data class SelectCategory(val id: CategoryId) : FundCountUiAction
    data object Confirm : FundCountUiAction
}

/** S53: "¿cuánto te queda?"; muestra el gastado real contra el estimado y lo que se cargaría como gasto no registrado. */
@HiltViewModel(assistedFactory = FundCountViewModel.Factory::class)
class FundCountViewModel @AssistedInject constructor(
    @Assisted fundIdValue: String,
    private val funds: FundRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    clock: Clock,
    private val countFund: CountFundUseCase,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(fundIdValue: String): FundCountViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    private val fundId = FundId(fundIdValue)
    private val today = LocalDate.now(clock)

    private data class Form(
        val loaded: Boolean = false,
        val overview: FundOverview? = null,
        val declaredText: String = "",
        val accountId: AccountId? = null,
        val categoryId: CategoryId? = null,
        val error: BusinessError? = null,
        val saved: Boolean = false,
        val submitting: Boolean = false,
    )

    private val form = MutableStateFlow(Form())

    val uiState: StateFlow<FundCountUiState> = combine(form, accounts.observeAccounts(), categories.observeAll()) { form, accountList, categoryList ->
        val overview = form.overview
        val declared = MoneyInputFilter.toMinor(form.declaredText)?.let { Money(it, CurrencyCode.PEN) }
        val untracked = if (overview != null && declared != null) preview(overview, declared) else null
        FundCountUiState(
            loaded = form.loaded,
            notFound = form.loaded && overview == null,
            overview = overview,
            declaredText = form.declaredText,
            accountId = form.accountId,
            categoryId = form.categoryId,
            accountOptions = accountList.filter { !it.account.isArchived }.map { EntityOption(it.account.id.value, it.account.name) },
            categoryOptions = categoryList.filter { !it.isArchived }.map { EntityOption(it.id.value, it.name) },
            realSpent = if (untracked is Outcome.Ok && overview != null && declared != null) overview.fund.allocated - declared else null,
            estimatedSpent = overview?.estimatedSpent,
            untracked = (untracked as? Outcome.Ok)?.value,
            rejection = (untracked as? Outcome.Failed)?.error,
            suggestBeforeClose = overview?.fund?.endsOn?.let { !today.isBefore(it) } == true,
            error = form.error,
            saved = form.saved,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FundCountUiState())

    init {
        viewModelScope.launch {
            val overview = funds.find(fundId)
            val account = accounts.observeAccounts().first().firstOrNull { !it.account.isArchived }?.account?.id
            val category = categories.observeAll().first().firstOrNull { !it.isArchived }?.id
            form.update { it.copy(loaded = true, overview = overview, accountId = account, categoryId = category) }
        }
    }

    fun onAction(action: FundCountUiAction) {
        when (action) {
            is FundCountUiAction.EditDeclared -> edit { copy(declaredText = MoneyInputFilter.sanitize(action.text)) }
            is FundCountUiAction.SelectAccount -> edit { copy(accountId = action.id) }
            is FundCountUiAction.SelectCategory -> edit { copy(categoryId = action.id) }
            FundCountUiAction.Confirm -> confirm()
        }
    }

    private fun edit(change: Form.() -> Form) = form.update { it.change().copy(error = null) }

    private fun preview(overview: FundOverview, declared: Money): Outcome<Money> =
        FundRules.untrackedSpend(overview.fund.allocated, overview.detailedSpent, overview.fund.untrackedSpent, declared)

    private fun confirm() {
        val current = form.value
        if (current.submitting) return
        val declared = MoneyInputFilter.toMinor(current.declaredText)?.let { Money(it, CurrencyCode.PEN) }
            ?: return form.update { it.copy(error = BusinessError.InvalidAmount) }
        form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val error = attempt { countFund(fundId, declared, current.accountId, current.categoryId) }.errorOrNull()
            form.update { if (error == null) it.copy(saved = true, submitting = false) else it.copy(error = error, submitting = false) }
        }
    }
}
