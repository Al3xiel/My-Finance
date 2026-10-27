package com.al3xiel.my_finances.feature.movements.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
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

data class IncomeEditorUiState(
    val isCorrection: Boolean = false,
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val amountText: String = "",
    val accountId: AccountId? = null,
    val sourceId: IncomeSourceId? = null,
    val occurredOn: LocalDate = LocalDate.EPOCH,
    val today: LocalDate = LocalDate.EPOCH,
    val note: String = "",
    val accountOptions: List<EntityOption> = emptyList(),
    val sourceOptions: List<EntityOption> = emptyList(),
    val error: BusinessError? = null,
    val negativeWarning: BusinessError.WouldGoNegative? = null,
    val showReasonDialog: Boolean = false,
    val saved: Boolean = false,
)

sealed interface IncomeEditorUiAction {
    data class EditAmount(val text: String) : IncomeEditorUiAction
    data class SelectAccount(val id: AccountId) : IncomeEditorUiAction
    data class SelectSource(val id: IncomeSourceId) : IncomeEditorUiAction
    data class SelectOccurredOn(val occurredOn: LocalDate) : IncomeEditorUiAction
    data class EditNote(val text: String) : IncomeEditorUiAction
    data object Save : IncomeEditorUiAction
    data class ConfirmReason(val reason: String) : IncomeEditorUiAction
    data object DismissReason : IncomeEditorUiAction
    data object ConfirmNegativeBalance : IncomeEditorUiAction
    data object DismissWarning : IncomeEditorUiAction
}

/** S23: registra un ingreso nuevo, o corrige uno existente (con motivo) si recibe su id. */
@HiltViewModel(assistedFactory = IncomeEditorViewModel.Factory::class)
class IncomeEditorViewModel @AssistedInject constructor(
    @Assisted movementIdValue: String?,
    private val movements: MovementRepository,
    private val accounts: AccountRepository,
    private val incomeSources: IncomeSourceRepository,
    private val registerIncome: RegisterIncomeUseCase,
    private val correctMovement: CorrectMovementUseCase,
    clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(movementIdValue: String?): IncomeEditorViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    private val movementId = movementIdValue?.let(::MovementId)
    private val today = LocalDate.now(clock)

    private data class Form(
        val loaded: Boolean = false,
        val notFound: Boolean = false,
        val amountText: String = "",
        val accountId: AccountId? = null,
        val sourceId: IncomeSourceId? = null,
        val occurredOn: LocalDate,
        val note: String = "",
        val error: BusinessError? = null,
        val negativeWarning: BusinessError.WouldGoNegative? = null,
        val showReasonDialog: Boolean = false,
        val pendingReason: String? = null,
        val saved: Boolean = false,
        val submitting: Boolean = false,
    )

    private val form = MutableStateFlow(Form(occurredOn = today))

    val uiState: StateFlow<IncomeEditorUiState> = combine(form, accounts.observeAccounts(), incomeSources.observeAll()) { form, accountList, sourceList ->
        IncomeEditorUiState(
            isCorrection = movementId != null,
            loaded = form.loaded,
            notFound = form.notFound,
            amountText = form.amountText,
            accountId = form.accountId,
            sourceId = form.sourceId,
            occurredOn = form.occurredOn,
            today = today,
            note = form.note,
            accountOptions = accountList.filter { !it.account.isArchived }.map { EntityOption(it.account.id.value, it.account.name) },
            // Las fuentes del sistema (saldo inicial, ajuste, reembolso…) las usan otras funciones, no el usuario.
            sourceOptions = sourceList.filter { it.kind == IncomeSourceKind.REGULAR || it.id == form.sourceId }
                .map { EntityOption(it.id.value, it.name) },
            error = form.error,
            negativeWarning = form.negativeWarning,
            showReasonDialog = form.showReasonDialog,
            saved = form.saved,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, IncomeEditorUiState(occurredOn = today, today = today))

    init {
        viewModelScope.launch { initialize() }
    }

    private suspend fun initialize() {
        if (movementId == null) {
            val account = accounts.observeAccounts().first().firstOrNull { !it.account.isArchived }?.account?.id
            val source = incomeSources.observeAll().first().firstOrNull { it.kind == IncomeSourceKind.REGULAR }?.id
            form.update { it.copy(loaded = true, accountId = account, sourceId = source) }
            return
        }
        val movement = movements.find(movementId)
        if (movement == null || movement.type != MovementType.INCOME || movement.status != MovementStatus.ACTIVE) {
            form.update { it.copy(loaded = true, notFound = true) }
            return
        }
        form.update {
            it.copy(
                loaded = true,
                amountText = minorToText(movement.amount.amountMinor),
                accountId = movement.accountId,
                sourceId = movement.incomeSourceId,
                occurredOn = movement.occurredOn,
                note = movement.note.orEmpty(),
            )
        }
    }

    fun onAction(action: IncomeEditorUiAction) {
        when (action) {
            is IncomeEditorUiAction.EditAmount -> edit { copy(amountText = MoneyInputFilter.sanitize(action.text)) }
            is IncomeEditorUiAction.SelectAccount -> edit { copy(accountId = action.id) }
            is IncomeEditorUiAction.SelectSource -> edit { copy(sourceId = action.id) }
            is IncomeEditorUiAction.SelectOccurredOn -> edit { copy(occurredOn = action.occurredOn) }
            is IncomeEditorUiAction.EditNote -> edit { copy(note = action.text) }
            IncomeEditorUiAction.Save ->
                if (movementId != null && form.value.pendingReason == null) {
                    form.update { it.copy(showReasonDialog = true) }
                } else {
                    submit(confirmNegative = false)
                }
            is IncomeEditorUiAction.ConfirmReason -> {
                form.update { it.copy(pendingReason = action.reason, showReasonDialog = false) }
                submit(confirmNegative = false)
            }
            IncomeEditorUiAction.DismissReason -> form.update { it.copy(showReasonDialog = false) }
            IncomeEditorUiAction.ConfirmNegativeBalance -> {
                form.update { it.copy(negativeWarning = null) }
                submit(confirmNegative = true)
            }
            IncomeEditorUiAction.DismissWarning -> form.update { it.copy(negativeWarning = null, pendingReason = null) }
        }
    }

    private fun edit(change: Form.() -> Form) = form.update { it.change().copy(error = null) }

    private fun submit(confirmNegative: Boolean) {
        val current = form.value
        if (current.submitting) return
        val amount = MoneyInputFilter.toMinor(current.amountText)?.let { Money(it, CurrencyCode.PEN) }
            ?: return fail(BusinessError.InvalidAmount)
        val account = current.accountId ?: return fail(BusinessError.NotFound)
        val source = current.sourceId ?: return fail(BusinessError.NotFound)

        form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val note = current.note.trim().ifEmpty { null }
            val outcome = attempt {
                if (movementId == null) {
                    registerIncome(account, amount, source, note, current.occurredOn)
                } else {
                    correctMovement(
                        movementId,
                        MovementDraft.Income(account, amount, source, note, current.occurredOn),
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

    private fun minorToText(minor: Long): String {
        val cents = minor % 100
        return if (cents == 0L) (minor / 100).toString() else "${minor / 100}.${cents.toString().padStart(2, '0').trimEnd('0')}"
    }
}
