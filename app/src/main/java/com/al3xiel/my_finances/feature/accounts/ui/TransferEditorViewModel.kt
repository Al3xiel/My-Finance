package com.al3xiel.my_finances.feature.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.FeeData
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RegisterTransferUseCase
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
import javax.inject.Inject

/** Etiqueta automática: no se guarda, se deduce de los tipos de cuenta (docs/04 §3.2). */
enum class TransferLabel { WITHDRAWAL, DEPOSIT, TRANSFER }

data class TransferEditorUiState(
    val loaded: Boolean = false,
    val fromId: AccountId? = null,
    val toId: AccountId? = null,
    val amountText: String = "",
    val feeText: String = "",
    val occurredOn: LocalDate = LocalDate.EPOCH,
    val today: LocalDate = LocalDate.EPOCH,
    val note: String = "",
    val label: TransferLabel = TransferLabel.TRANSFER,
    val accountOptions: List<EntityOption> = emptyList(),
    val error: BusinessError? = null,
    val negativeWarning: BusinessError.WouldGoNegative? = null,
    val saved: Boolean = false,
)

sealed interface TransferEditorUiAction {
    data class SelectFrom(val id: AccountId) : TransferEditorUiAction
    data class SelectTo(val id: AccountId) : TransferEditorUiAction
    data class EditAmount(val text: String) : TransferEditorUiAction
    data class EditFee(val text: String) : TransferEditorUiAction
    data class SelectOccurredOn(val occurredOn: LocalDate) : TransferEditorUiAction
    data class EditNote(val text: String) : TransferEditorUiAction
    data object Save : TransferEditorUiAction
    data object ConfirmNegativeBalance : TransferEditorUiAction
    data object DismissWarning : TransferEditorUiAction
}

/** S24: solo crea; un error se revierte con otra transferencia en sentido contrario (D12). */
@HiltViewModel
class TransferEditorViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val registerTransfer: RegisterTransferUseCase,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)

    private data class Form(
        val loaded: Boolean = false,
        val fromId: AccountId? = null,
        val toId: AccountId? = null,
        val amountText: String = "",
        val feeText: String = "",
        val occurredOn: LocalDate,
        val note: String = "",
        val error: BusinessError? = null,
        val negativeWarning: BusinessError.WouldGoNegative? = null,
        val saved: Boolean = false,
        val submitting: Boolean = false,
    )

    private val form = MutableStateFlow(Form(occurredOn = today))

    val uiState: StateFlow<TransferEditorUiState> = combine(form, accounts.observeAccounts()) { form, accountList ->
        val active = accountList.filter { !it.account.isArchived }
        TransferEditorUiState(
            loaded = form.loaded,
            fromId = form.fromId,
            toId = form.toId,
            amountText = form.amountText,
            feeText = form.feeText,
            occurredOn = form.occurredOn,
            today = today,
            note = form.note,
            label = label(active, form.fromId, form.toId),
            accountOptions = active.map { EntityOption(it.account.id.value, it.account.name) },
            error = form.error,
            negativeWarning = form.negativeWarning,
            saved = form.saved,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TransferEditorUiState(occurredOn = today, today = today))

    init {
        viewModelScope.launch {
            val active = accounts.observeAccounts().first().filter { !it.account.isArchived }
            form.update { it.copy(loaded = true, fromId = active.getOrNull(0)?.account?.id, toId = active.getOrNull(1)?.account?.id) }
        }
    }

    fun onAction(action: TransferEditorUiAction) {
        when (action) {
            is TransferEditorUiAction.SelectFrom -> edit { copy(fromId = action.id) }
            is TransferEditorUiAction.SelectTo -> edit { copy(toId = action.id) }
            is TransferEditorUiAction.EditAmount -> edit { copy(amountText = MoneyInputFilter.sanitize(action.text)) }
            is TransferEditorUiAction.EditFee -> edit { copy(feeText = MoneyInputFilter.sanitize(action.text)) }
            is TransferEditorUiAction.SelectOccurredOn -> edit { copy(occurredOn = action.occurredOn) }
            is TransferEditorUiAction.EditNote -> edit { copy(note = action.text) }
            TransferEditorUiAction.Save -> submit(confirmNegative = false)
            TransferEditorUiAction.ConfirmNegativeBalance -> {
                form.update { it.copy(negativeWarning = null) }
                submit(confirmNegative = true)
            }
            TransferEditorUiAction.DismissWarning -> form.update { it.copy(negativeWarning = null) }
        }
    }

    private fun edit(change: Form.() -> Form) = form.update { it.change().copy(error = null) }

    private fun submit(confirmNegative: Boolean) {
        val current = form.value
        if (current.submitting) return
        val amount = MoneyInputFilter.toMinor(current.amountText)?.let { Money(it, CurrencyCode.PEN) }
            ?: return fail(BusinessError.InvalidAmount)
        val from = current.fromId ?: return fail(BusinessError.NotFound)
        val to = current.toId ?: return fail(BusinessError.NotFound)
        // Sin texto de comisión no hay comisión; con texto (aunque sea 0) el caso de uso valida el monto.
        val fee = MoneyInputFilter.toMinor(current.feeText)?.let { FeeData(Money(it, CurrencyCode.PEN), SystemCatalog.feeCategoryId) }

        form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val note = current.note.trim().ifEmpty { null }
            val outcome = attempt { registerTransfer(from, to, amount, fee, note, current.occurredOn, confirmNegative) }
            when (val error = outcome.errorOrNull()) {
                null -> form.update { it.copy(saved = true, submitting = false) }
                is BusinessError.WouldGoNegative -> form.update { it.copy(negativeWarning = error, submitting = false) }
                else -> form.update { it.copy(error = error, submitting = false) }
            }
        }
    }

    private fun fail(error: BusinessError) = form.update { it.copy(error = error) }

    private fun label(active: List<AccountWithBalance>, from: AccountId?, to: AccountId?): TransferLabel {
        val fromType = active.firstOrNull { it.account.id == from }?.account?.type
        val toType = active.firstOrNull { it.account.id == to }?.account?.type
        return when {
            fromType == AccountType.BANK && toType == AccountType.CASH -> TransferLabel.WITHDRAWAL
            fromType == AccountType.CASH && toType == AccountType.BANK -> TransferLabel.DEPOSIT
            else -> TransferLabel.TRANSFER
        }
    }
}
