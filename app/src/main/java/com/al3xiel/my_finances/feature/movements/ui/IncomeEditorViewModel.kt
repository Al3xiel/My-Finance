package com.al3xiel.my_finances.feature.movements.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeRules
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeSuggestion
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeSuggestions
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.ConfirmExpectedIncomeUseCase
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.usecase.RegisterIncomeWithTopUpUseCase
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
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
    /** Viene de S61: sin recarga a fondos ni ingreso esperado, y queda vinculado a la conciliación. */
    val linkedToReconciliation: Boolean = false,
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val amountText: String = "",
    val accountId: AccountId? = null,
    val sourceId: IncomeSourceId? = null,
    val occurredOn: LocalDate = LocalDate.ofEpochDay(0),
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val note: String = "",
    val accountOptions: List<EntityOption> = emptyList(),
    val sourceOptions: List<EntityOption> = emptyList(),
    /** Fondos activos para apartar parte del ingreso; vacío al corregir (una recarga no se edita aquí). */
    val fundOptions: List<EntityOption> = emptyList(),
    val topUpFundId: FundId? = null,
    val topUpText: String = "",
    /** Pendientes de la misma cuenta y fuente que se sugieren confirmar; vacío al corregir o con uno ya elegido. */
    val suggestions: List<ExpectedIncomeSuggestion> = emptyList(),
    /** El esperado que este ingreso confirmará al guardar. */
    val selectedExpected: ExpectedIncomeSuggestion? = null,
    val error: BusinessError? = null,
    val negativeWarning: BusinessError.WouldGoNegative? = null,
    val showReasonDialog: Boolean = false,
    val saved: Boolean = false,
    val saving: Boolean = false,
)

/** S23: registra un ingreso nuevo, o corrige uno existente (con motivo) si recibe su id. */
@HiltViewModel(assistedFactory = IncomeEditorViewModel.Factory::class)
class IncomeEditorViewModel @AssistedInject constructor(
    @Assisted("movement") movementIdValue: String?,
    @Assisted("expectedIncome") expectedIncomeIdValue: String?,
    private val movements: MovementRepository,
    private val accounts: AccountRepository,
    private val incomeSources: IncomeSourceRepository,
    funds: FundRepository,
    private val registerIncome: RegisterIncomeUseCase,
    private val registerIncomeWithTopUp: RegisterIncomeWithTopUpUseCase,
    private val correctMovement: CorrectMovementUseCase,
    private val expectedIncomes: ExpectedIncomeRepository,
    private val confirmExpectedIncome: ConfirmExpectedIncomeUseCase,
    clock: Clock,
    private val reconciliations: ReconciliationRepository,
    @Assisted("reconciliation") reconciliationIdValue: String? = null,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("movement") movementIdValue: String?,
            @Assisted("expectedIncome") expectedIncomeIdValue: String?,
            @Assisted("reconciliation") reconciliationIdValue: String?,
        ): IncomeEditorViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    private val movementId = movementIdValue?.let(::MovementId)
    private val presetExpectedId = expectedIncomeIdValue?.let(::ExpectedIncomeId)
    private val reconciliationId = reconciliationIdValue?.let(::ReconciliationId)
    private val today = LocalDate.now(clock)

    private data class Form(
        val loaded: Boolean = false,
        val notFound: Boolean = false,
        val amountText: String = "",
        val accountId: AccountId? = null,
        val sourceId: IncomeSourceId? = null,
        val occurredOn: LocalDate,
        val note: String = "",
        val topUpFundId: FundId? = null,
        val topUpText: String = "",
        val expectedIncomeId: ExpectedIncomeId? = null,
        val error: BusinessError? = null,
        val negativeWarning: BusinessError.WouldGoNegative? = null,
        val showReasonDialog: Boolean = false,
        val pendingReason: String? = null,
        val saved: Boolean = false,
        val submitting: Boolean = false,
    )

    private val form = MutableStateFlow(Form(occurredOn = today))

    val uiState: StateFlow<IncomeEditorUiState> = combine(
        form, accounts.observeAccounts(), incomeSources.observeAll(), funds.observeFunds(), expectedIncomes.observeAll(),
    ) { form, accountList, sourceList, fundList, expectedList ->
        val sourceNames = sourceList.associate { it.id to it.name }
        val selected = if (reconciliationId != null) null else ExpectedIncomeSuggestions.selected(expectedList, sourceNames, form.expectedIncomeId)
        IncomeEditorUiState(
            isCorrection = movementId != null,
            linkedToReconciliation = reconciliationId != null,
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
            fundOptions = if (movementId != null || reconciliationId != null) emptyList() else fundList.filter { it.fund.status == FundStatus.ACTIVE }.map { EntityOption(it.fund.id.value, it.fund.name) },
            topUpFundId = form.topUpFundId,
            topUpText = form.topUpText,
            selectedExpected = selected,
            suggestions = if (movementId != null || reconciliationId != null || selected != null) {
                emptyList()
            } else {
                ExpectedIncomeSuggestions.of(expectedList, sourceNames, form.accountId, form.sourceId)
            },
            error = form.error,
            negativeWarning = form.negativeWarning,
            showReasonDialog = form.showReasonDialog,
            saved = form.saved,
            saving = form.submitting,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, IncomeEditorUiState(occurredOn = today, today = today))

    init {
        viewModelScope.launch { initialize() }
    }

    private suspend fun initialize() {
        if (movementId == null && presetExpectedId != null && reconciliationId == null) {
            // Desde S55: cuenta, fuente y monto salen del esperado; el usuario puede ajustarlos antes de guardar.
            val expected = expectedIncomes.find(presetExpectedId)
            if (expected == null || !ExpectedIncomeRules.isPending(expected.status)) {
                form.update { it.copy(loaded = true, notFound = true) }
            } else {
                form.update {
                    it.copy(
                        loaded = true,
                        accountId = expected.accountId,
                        sourceId = expected.incomeSourceId,
                        amountText = MoneyInputFilter.fromMinor(expected.estimated.amountMinor),
                        expectedIncomeId = expected.id,
                    )
                }
            }
            return
        }
        if (movementId == null) {
            val reconciled = reconciliationId?.let { reconciliations.find(it) }?.accountId
            val account = reconciled ?: accounts.observeAccounts().first().firstOrNull { !it.account.isArchived }?.account?.id
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
                amountText = MoneyInputFilter.fromMinor(movement.amount.amountMinor),
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
            is IncomeEditorUiAction.SelectTopUpFund ->
                edit { copy(topUpFundId = action.id, topUpText = if (action.id == null) "" else topUpText.ifEmpty { amountText }) }
            is IncomeEditorUiAction.SelectExpected -> viewModelScope.launch {
                val expected = expectedIncomes.find(action.id) ?: return@launch
                edit {
                    copy(expectedIncomeId = expected.id, amountText = amountText.ifEmpty { MoneyInputFilter.fromMinor(expected.estimated.amountMinor) })
                }
            }
            IncomeEditorUiAction.ClearExpected -> edit { copy(expectedIncomeId = null) }
            is IncomeEditorUiAction.EditTopUp -> edit { copy(topUpText = MoneyInputFilter.sanitize(action.text)) }
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
        val account = current.accountId ?: return fail(BusinessError.AccountRequired)
        val source = current.sourceId ?: return fail(BusinessError.IncomeSourceRequired)
        val topUpFund = current.topUpFundId
        val topUp = topUpFund?.let { MoneyInputFilter.toMinor(current.topUpText)?.let { minor -> Money(minor, CurrencyCode.PEN) } ?: return fail(BusinessError.InvalidAmount) }

        form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val note = current.note.trim().ifEmpty { null }
            val outcome = attempt {
                val expectedId = current.expectedIncomeId
                if (movementId == null && expectedId != null && reconciliationId == null) {
                    confirmExpectedIncome(expectedId, account, amount, source, note, current.occurredOn, topUpFund, topUp)
                } else if (movementId == null && reconciliationId == null && topUpFund != null && topUp != null) {
                    registerIncomeWithTopUp(account, amount, source, note, current.occurredOn, topUpFund, topUp)
                } else if (movementId == null) {
                    registerIncome(account, amount, source, note, current.occurredOn, reconciliationId)
                } else {
                    correctMovement(
                        movementId,
                        MovementDraft.Income(account, amount, source, note, current.occurredOn),
                        current.pendingReason.orEmpty(),
                        confirmNegative,
                        reconciliationId,
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
}
