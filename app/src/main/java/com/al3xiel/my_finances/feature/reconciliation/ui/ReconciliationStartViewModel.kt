package com.al3xiel.my_finances.feature.reconciliation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationRules
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.RecordBalanceCheckUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReconciliationStartUiState(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val accountName: String? = null,
    val currency: CurrencyCode = CurrencyCode.PEN,
    val appBalance: Money? = null,
    val realText: String = "",
    /** Banco menos app; `null` mientras lo tecleado no es un monto. */
    val difference: Money? = null,
    val error: BusinessError? = null,
    /** La cuenta ya tenía una conciliación abierta: la pantalla salta a ella. */
    val openReconciliationId: ReconciliationId? = null,
    /** Se registró el saldo del banco: la pantalla sigue a la conciliación recién abierta. */
    val recordedId: ReconciliationId? = null,
)

sealed interface ReconciliationStartUiAction {
    data class EditReal(val text: String) : ReconciliationStartUiAction
    data object Confirm : ReconciliationStartUiAction
}

/** S60: "¿Cuánto dice tu banco?". Registrar no mueve ningún saldo (C1). */
@HiltViewModel(assistedFactory = ReconciliationStartViewModel.Factory::class)
class ReconciliationStartViewModel @AssistedInject constructor(
    @Assisted accountIdValue: String,
    private val accounts: AccountRepository,
    private val reconciliations: ReconciliationRepository,
    private val recordBalanceCheck: RecordBalanceCheckUseCase,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(accountIdValue: String): ReconciliationStartViewModel
    }

    private val accountId = AccountId(accountIdValue)
    private val state = MutableStateFlow(ReconciliationStartUiState())
    private var submitting = false

    val uiState: StateFlow<ReconciliationStartUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val account = accounts.find(accountId)
            val open = reconciliations.openFor(accountId)
            val balance = account?.let { Money(accounts.balanceMinor(accountId), it.currency) }
            state.update {
                it.copy(
                    loaded = true,
                    notFound = account == null,
                    accountName = account?.name,
                    currency = account?.currency ?: it.currency,
                    appBalance = balance,
                    openReconciliationId = open?.id,
                )
            }
        }
    }

    fun onAction(action: ReconciliationStartUiAction) {
        when (action) {
            is ReconciliationStartUiAction.EditReal -> state.update {
                val text = MoneyInputFilter.sanitize(action.text, allowNegative = true)
                it.copy(realText = text, difference = differenceOf(text, it), error = null)
            }
            ReconciliationStartUiAction.Confirm -> confirm()
        }
    }

    private fun differenceOf(text: String, current: ReconciliationStartUiState): Money? {
        val app = current.appBalance ?: return null
        val real = MoneyInputFilter.toSignedMinor(text)?.let { Money(it, current.currency) } ?: return null
        return ReconciliationRules.remainingDifference(real, app)
    }

    private fun confirm() {
        val current = state.value
        if (submitting) return
        val real = MoneyInputFilter.toSignedMinor(current.realText)?.let { Money(it, current.currency) }
            ?: return state.update { it.copy(error = BusinessError.InvalidAmount) }
        submitting = true
        viewModelScope.launch {
            val result = attempt { recordBalanceCheck(accountId, real) }
            submitting = false
            state.update {
                when (result) {
                    is Outcome.Ok -> it.copy(recordedId = result.value)
                    is Outcome.Failed -> it.copy(error = result.error)
                }
            }
        }
    }
}
