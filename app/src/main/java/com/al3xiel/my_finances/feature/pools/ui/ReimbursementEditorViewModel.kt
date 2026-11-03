package com.al3xiel.my_finances.feature.pools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.domain.model.event.AllocationData
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import com.al3xiel.my_finances.feature.pools.domain.model.PendingPartLine
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.pools.domain.rules.OldestFirstAllocator
import com.al3xiel.my_finances.feature.pools.domain.usecase.ReceiveReimbursementUseCase
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

/** S34: registra un reembolso; muestra qué partes saldaría y bloquea el exceso (D1). */
@HiltViewModel(assistedFactory = ReimbursementEditorViewModel.Factory::class)
class ReimbursementEditorViewModel @AssistedInject constructor(
    @Assisted("person") personIdValue: String?,
    @Assisted("pool") poolIdValue: String?,
    people: PersonRepository,
    accounts: AccountRepository,
    private val pools: PoolRepository,
    private val receive: ReceiveReimbursementUseCase,
    clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(@Assisted("person") personIdValue: String?, @Assisted("pool") poolIdValue: String?): ReimbursementEditorViewModel
    }

    private val today = LocalDate.now(clock)
    private val poolId = poolIdValue?.let(::PoolId)

    private data class Form(
        val loaded: Boolean = false,
        val personId: PersonId? = null,
        val accountId: AccountId? = null,
        val amountText: String = "",
        val strategy: AllocationStrategy = AllocationStrategy.OLDEST_FIRST,
        val occurredOn: LocalDate,
        val note: String = "",
        val lines: List<PendingPartLine> = emptyList(),
        val manual: Map<PartId, String> = emptyMap(),
        val error: BusinessError? = null,
        val saved: Boolean = false,
        val submitting: Boolean = false,
    )

    private val form = MutableStateFlow(Form(personId = personIdValue?.let(::PersonId), occurredOn = today))

    val uiState: StateFlow<ReimbursementEditorUiState> = combine(form, people.observeAll(), accounts.observeAccounts()) { form, peopleList, accountList ->
        val activeAccounts = accountList.filter { !it.account.isArchived }
        val amount = MoneyInputFilter.toMinor(form.amountText) ?: 0L
        val pendingTotal = form.lines.sumOf { it.pending.amountMinor }
        val settle = settleByPart(form, amount)
        ReimbursementEditorUiState(
            loaded = form.loaded,
            personId = form.personId,
            poolId = poolId,
            personOptions = peopleList.filter { !it.isArchived }.map { EntityOption(it.id.value, it.alias) },
            accountOptions = activeAccounts.map { EntityOption(it.account.id.value, it.account.name) },
            accountId = form.accountId,
            amountText = form.amountText,
            strategy = form.strategy,
            occurredOn = form.occurredOn,
            today = today,
            note = form.note,
            lines = form.lines.map { ReimbursementLineUi(it, Money(settle[it.partId] ?: 0L, CurrencyCode.PEN), form.manual[it.partId].orEmpty()) },
            pendingTotal = Money(pendingTotal, CurrencyCode.PEN),
            exceedsPending = amount > pendingTotal,
            unassigned = Money(amount - settle.values.sum(), CurrencyCode.PEN),
            error = form.error,
            saved = form.saved,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReimbursementEditorUiState(occurredOn = today, today = today))

    init {
        viewModelScope.launch {
            val firstAccount = accounts.observeAccounts().first()
                .firstOrNull { !it.account.isArchived }?.account?.id
            val person = form.value.personId
            val lines = if (person != null) ordered(pools.pendingPartLines(person, poolId)) else emptyList()
            form.update { it.copy(loaded = true, accountId = firstAccount, lines = lines) }
        }
    }

    /** Del gasto más antiguo al más reciente: es el orden en que `OLDEST_FIRST` las salda. */
    private fun ordered(lines: List<PendingPartLine>) = lines.sortedWith(compareBy({ it.occurredOn }, { it.createdSequence }))

    /** Lo que saldaría cada parte: el reparto calculado (más antiguos primero) o lo tecleado (manual). */
    private fun settleByPart(form: Form, amount: Long): Map<PartId, Long> = when (form.strategy) {
        AllocationStrategy.OLDEST_FIRST -> when (val result = OldestFirstAllocator.allocate(form.lines.map { it.toPendingPart() }, Money(amount, CurrencyCode.PEN))) {
            is Outcome.Ok -> result.value.associate { it.partId to it.amount.amountMinor }
            is Outcome.Failed -> emptyMap()
        }
        AllocationStrategy.MANUAL -> form.manual.mapNotNull { (id, text) -> MoneyInputFilter.toMinor(text)?.takeIf { it > 0 }?.let { id to it } }.toMap()
    }

    fun onAction(action: ReimbursementEditorUiAction) {
        when (action) {
            is ReimbursementEditorUiAction.SelectPerson -> selectPerson(action.id)
            is ReimbursementEditorUiAction.SelectAccount -> edit { copy(accountId = action.id) }
            is ReimbursementEditorUiAction.EditAmount -> edit { copy(amountText = MoneyInputFilter.sanitize(action.text)) }
            is ReimbursementEditorUiAction.SelectStrategy -> edit { copy(strategy = action.strategy) }
            is ReimbursementEditorUiAction.EditManual -> edit { copy(manual = manual + (action.partId to MoneyInputFilter.sanitize(action.text))) }
            is ReimbursementEditorUiAction.SelectDate -> edit { copy(occurredOn = action.date) }
            is ReimbursementEditorUiAction.EditNote -> edit { copy(note = action.text) }
            ReimbursementEditorUiAction.Save -> save()
        }
    }

    private fun selectPerson(id: PersonId) {
        form.update { it.copy(personId = id, lines = emptyList(), manual = emptyMap(), error = null) }
        viewModelScope.launch {
            val lines = ordered(pools.pendingPartLines(id, poolId))
            form.update { if (it.personId == id) it.copy(lines = lines) else it }
        }
    }

    private fun edit(change: Form.() -> Form) = form.update { it.change().copy(error = null) }

    private fun save() {
        val current = form.value
        if (current.submitting) return
        val amount = MoneyInputFilter.toMinor(current.amountText)?.takeIf { it > 0 }?.let { Money(it, CurrencyCode.PEN) }
            ?: return fail(BusinessError.InvalidAmount)
        val person = current.personId ?: return fail(BusinessError.PersonRequired)
        val account = current.accountId ?: return fail(BusinessError.NotFound)
        if (amount.amountMinor > current.lines.sumOf { it.pending.amountMinor }) return fail(BusinessError.ExceedsPending)

        val manual = current.lines.mapNotNull { line ->
            current.manual[line.partId]?.let(MoneyInputFilter::toMinor)?.takeIf { it > 0 }
                ?.let { AllocationData(line.partId, Money(it, CurrencyCode.PEN)) }
        }
        form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val note = current.note.trim().ifEmpty { null }
            val outcome = attempt { receive(person, account, amount, current.strategy, poolId, manual, note, current.occurredOn) }
            val error = outcome.errorOrNull()
            form.update { if (error == null) it.copy(saved = true, submitting = false) else it.copy(error = error, submitting = false) }
        }
    }

    private fun fail(error: BusinessError) = form.update { it.copy(error = error) }
}
