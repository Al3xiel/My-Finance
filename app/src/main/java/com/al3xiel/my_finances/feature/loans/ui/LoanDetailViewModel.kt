package com.al3xiel.my_finances.feature.loans.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.loans.domain.model.LoanDetail
import com.al3xiel.my_finances.feature.loans.domain.repository.LoanRepository
import com.al3xiel.my_finances.feature.loans.domain.usecase.ReceiveLoanRepaymentUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class LoanPayDialog(
    val amountText: String = "",
    val accountId: AccountId? = null,
    val error: BusinessError? = null,
)

/** Lo que se va a anular: el préstamo o uno de sus pagos; ambos son `MovementVoided` sobre su movimiento (D20). */
data class LoanVoidTarget(val movementId: MovementId, val isLoan: Boolean)

data class LoanDetailUiState(
    val loaded: Boolean = false,
    val detail: LoanDetail? = null,
    val accountOptions: List<EntityOption> = emptyList(),
    val pay: LoanPayDialog? = null,
    val voiding: LoanVoidTarget? = null,
    val negativeWarning: BusinessError.WouldGoNegative? = null,
    val notice: BusinessError? = null,
) {
    val notFound: Boolean get() = loaded && detail == null
}

sealed interface LoanDetailUiAction {
    data object OpenPay : LoanDetailUiAction
    data class EditPayAmount(val text: String) : LoanDetailUiAction
    data class SelectPayAccount(val id: AccountId) : LoanDetailUiAction
    data object ConfirmPay : LoanDetailUiAction
    data object DismissPay : LoanDetailUiAction
    data object AskVoidLoan : LoanDetailUiAction
    data class AskVoidRepayment(val movementId: MovementId) : LoanDetailUiAction
    data class ConfirmVoid(val reason: String) : LoanDetailUiAction
    data object ConfirmNegativeBalance : LoanDetailUiAction
    data object DismissWarning : LoanDetailUiAction
    data object DismissVoid : LoanDetailUiAction
    data object DismissNotice : LoanDetailUiAction
}

/** S36: la deuda, sus pagos, registrar un pago y anular el préstamo o un pago (D20). */
@HiltViewModel(assistedFactory = LoanDetailViewModel.Factory::class)
class LoanDetailViewModel @AssistedInject constructor(
    @Assisted loanIdValue: String,
    loans: LoanRepository,
    private val accounts: AccountRepository,
    private val receivePayment: ReceiveLoanRepaymentUseCase,
    private val voidMovement: VoidMovementUseCase,
    clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(loanIdValue: String): LoanDetailViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    private val loanId = LoanId(loanIdValue)
    private val today = LocalDate.now(clock)

    private data class Local(
        val pay: LoanPayDialog? = null,
        val voiding: LoanVoidTarget? = null,
        val pendingReason: String? = null,
        val negativeWarning: BusinessError.WouldGoNegative? = null,
        val notice: BusinessError? = null,
        val submitting: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<LoanDetailUiState> = combine(loans.observeDetail(loanId), accounts.observeAccounts(), local) { detail, accountList, local ->
        LoanDetailUiState(
            loaded = true,
            detail = detail,
            accountOptions = accountList.filter { !it.account.isArchived }.map { EntityOption(it.account.id.value, it.account.name) },
            pay = local.pay,
            voiding = local.voiding,
            negativeWarning = local.negativeWarning,
            notice = local.notice,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoanDetailUiState())

    fun onAction(action: LoanDetailUiAction) {
        when (action) {
            LoanDetailUiAction.OpenPay -> openPay()
            is LoanDetailUiAction.EditPayAmount -> local.update { it.copy(pay = it.pay?.copy(amountText = MoneyInputFilter.sanitize(action.text), error = null)) }
            is LoanDetailUiAction.SelectPayAccount -> local.update { it.copy(pay = it.pay?.copy(accountId = action.id, error = null)) }
            LoanDetailUiAction.ConfirmPay -> confirmPay()
            LoanDetailUiAction.DismissPay -> local.update { it.copy(pay = null) }
            LoanDetailUiAction.AskVoidLoan -> uiState.value.detail?.let { d -> local.update { it.copy(voiding = LoanVoidTarget(d.loan.movementId, isLoan = true)) } }
            is LoanDetailUiAction.AskVoidRepayment -> local.update { it.copy(voiding = LoanVoidTarget(action.movementId, isLoan = false)) }
            is LoanDetailUiAction.ConfirmVoid -> local.value.voiding?.let { submitVoid(it, action.reason, confirmNegative = false) }
            LoanDetailUiAction.ConfirmNegativeBalance -> confirmNegative()
            LoanDetailUiAction.DismissWarning -> local.update { it.copy(negativeWarning = null, pendingReason = null, voiding = null) }
            LoanDetailUiAction.DismissVoid -> local.update { it.copy(voiding = null) }
            LoanDetailUiAction.DismissNotice -> local.update { it.copy(notice = null) }
        }
    }

    private fun openPay() {
        val first = uiState.value.accountOptions.firstOrNull()?.id?.let(::AccountId)
        local.update { it.copy(pay = LoanPayDialog(accountId = first)) }
    }

    private fun confirmPay() {
        val dialog = local.value.pay ?: return
        if (local.value.submitting) return
        val amount = MoneyInputFilter.toMinor(dialog.amountText)?.let { Money(it, CurrencyCode.PEN) }
            ?: return payError(BusinessError.InvalidAmount)
        val account = dialog.accountId ?: return payError(BusinessError.NotFound)
        local.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val error = attempt { receivePayment(loanId, account, amount, today) }.errorOrNull()
            local.update { if (error == null) it.copy(pay = null, submitting = false) else it.copy(pay = it.pay?.copy(error = error), submitting = false) }
        }
    }

    private fun payError(error: BusinessError) = local.update { it.copy(pay = it.pay?.copy(error = error)) }

    private fun confirmNegative() {
        val current = local.value
        val target = current.voiding ?: return
        val reason = current.pendingReason ?: return
        local.update { it.copy(negativeWarning = null) }
        submitVoid(target, reason, confirmNegative = true)
    }

    /** Un segundo toque mientras se guarda no emite otro `MovementVoided`. */
    private fun submitVoid(target: LoanVoidTarget, reason: String, confirmNegative: Boolean) {
        if (local.value.submitting) return
        local.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val error = attempt { voidMovement(target.movementId, reason, confirmNegative) }.errorOrNull()
            local.update {
                when (error) {
                    null -> it.copy(voiding = null, pendingReason = null, submitting = false)
                    is BusinessError.WouldGoNegative -> it.copy(submitting = false, pendingReason = reason, negativeWarning = error)
                    else -> it.copy(voiding = null, pendingReason = null, submitting = false, notice = error)
                }
            }
        }
    }
}
