package com.al3xiel.my_finances.feature.quickentry.ui

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
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.quickentry.domain.model.Ambiguity
import com.al3xiel.my_finances.feature.quickentry.domain.model.Candidate
import com.al3xiel.my_finances.feature.quickentry.domain.model.CatalogSnapshot
import com.al3xiel.my_finances.feature.quickentry.domain.model.Conflict
import com.al3xiel.my_finances.feature.quickentry.domain.model.EntityKind
import com.al3xiel.my_finances.feature.quickentry.domain.model.QuickEntryDraft
import com.al3xiel.my_finances.feature.quickentry.domain.rules.QuickEntryInterpreter
import com.al3xiel.my_finances.feature.quickentry.domain.usecase.ConfirmQuickEntryUseCase
import com.al3xiel.my_finances.feature.quickentry.domain.usecase.LoadCatalogSnapshotUseCase
import com.al3xiel.my_finances.feature.quickentry.domain.usecase.SuggestDefaultsUseCase
import com.al3xiel.my_finances.feature.quickentry.domain.usecase.SuggestedDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S25: interpreta una línea y muestra lo entendido; solo al confirmar guarda (D66).
 * Sin `SavedStateHandle` ni registros: el texto vive solo mientras la pantalla está abierta (D65).
 * Editar la línea vuelve a interpretar y reemplaza la vista previa; los cambios por selector se aplican encima.
 */
@HiltViewModel
class QuickEntryViewModel @Inject constructor(
    private val loadCatalog: LoadCatalogSnapshotUseCase,
    private val suggestDefaults: SuggestDefaultsUseCase,
    private val confirm: ConfirmQuickEntryUseCase,
) : ViewModel() {
    private data class Form(
        val snapshot: CatalogSnapshot = CatalogSnapshot(emptyList()),
        val line: String = "",
        val isIncome: Boolean = false,
        val amountText: String = "",
        val accountId: String? = null,
        val categoryId: String? = null,
        val tagIds: Set<String> = emptySet(),
        val poolId: String? = null,
        val fundId: String? = null,
        val sourceId: String? = null,
        val note: String = "",
        val ambiguities: List<Ambiguity> = emptyList(),
        val conflicts: Set<Conflict> = emptySet(),
        val suggested: SuggestedDefaults = SuggestedDefaults(null, null, emptySet()),
        val error: BusinessError? = null,
        val negativeWarning: BusinessError.WouldGoNegative? = null,
        val saved: Boolean = false,
        val submitting: Boolean = false,
    )

    private val form = MutableStateFlow(Form())
    private val _uiState = MutableStateFlow(QuickEntryUiState())
    val uiState: StateFlow<QuickEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val catalog = loadCatalog()
            form.update { it.copy(snapshot = catalog, suggested = suggestDefaults(null)) }
            publish()
        }
    }

    fun onAction(action: QuickEntryUiAction) {
        when (action) {
            is QuickEntryUiAction.EditLine -> interpret(action.text)
            is QuickEntryUiAction.EditAmount -> edit { copy(amountText = MoneyInputFilter.sanitize(action.text)) }
            is QuickEntryUiAction.EditNote -> edit { copy(note = action.text) }
            is QuickEntryUiAction.SelectAccount -> edit { copy(accountId = action.id, conflicts = conflicts - Conflict.ACCOUNTS) }
            is QuickEntryUiAction.SelectCategory -> edit { copy(categoryId = action.id, conflicts = conflicts - Conflict.CATEGORIES) }
            is QuickEntryUiAction.SelectSource -> edit { copy(sourceId = action.id, conflicts = conflicts - Conflict.SOURCES) }
            is QuickEntryUiAction.SelectPool -> edit(refresh = true) { withPool(action.id) }
            is QuickEntryUiAction.SelectFund -> edit { withFund(action.id) }
            is QuickEntryUiAction.Choose -> choose(action.ambiguity, action.candidate)
            QuickEntryUiAction.ClearPool -> edit(refresh = true) { copy(poolId = null) }
            QuickEntryUiAction.ClearFund -> edit { copy(fundId = null) }
            QuickEntryUiAction.Save -> submit(confirmNegative = false)
            QuickEntryUiAction.ConfirmNegativeBalance -> {
                form.update { it.copy(negativeWarning = null) }
                submit(confirmNegative = true)
            }
            QuickEntryUiAction.DismissWarning -> edit { copy(negativeWarning = null) }
        }
    }

    private fun interpret(text: String) {
        val result = QuickEntryInterpreter.interpret(text, form.value.snapshot)
        form.update {
            val pool = result.poolIds.singleOrNull()?.takeIf { result.fundIds.isEmpty() }
            val fund = result.fundIds.singleOrNull()?.takeIf { result.poolIds.isEmpty() }
            it.copy(
                line = text,
                isIncome = result.isIncome,
                amountText = result.amountMinor?.let(MoneyInputFilter::fromMinor).orEmpty(),
                accountId = result.accountIds.singleOrNull(),
                categoryId = result.categoryIds.singleOrNull(),
                tagIds = result.tagIds.toSet(),
                poolId = pool,
                fundId = fund,
                sourceId = result.sourceIds.singleOrNull(),
                note = result.note.orEmpty(),
                ambiguities = result.ambiguities,
                conflicts = result.conflicts,
                error = null,
            )
        }
        refreshSuggestions()
        publish()
    }

    private fun choose(ambiguity: Ambiguity, candidate: Candidate) = edit(refresh = candidate.kind == EntityKind.POOL) {
        val rest = copy(ambiguities = ambiguities - ambiguity)
        when (candidate.kind) {
            EntityKind.ACCOUNT -> rest.copy(accountId = candidate.id, conflicts = conflicts - Conflict.ACCOUNTS)
            EntityKind.CATEGORY -> rest.copy(categoryId = candidate.id, conflicts = conflicts - Conflict.CATEGORIES)
            EntityKind.TAG -> rest.copy(tagIds = tagIds + candidate.id)
            EntityKind.POOL -> rest.withPool(candidate.id)
            EntityKind.FUND -> rest.withFund(candidate.id)
            EntityKind.INCOME_SOURCE -> rest.copy(sourceId = candidate.id, conflicts = conflicts - Conflict.SOURCES)
        }
    }

    /** Un gasto va a un cúmulo o a un fondo, no a ambos: elegir uno suelta el otro. */
    private fun Form.withPool(id: String) =
        copy(poolId = id, fundId = null, conflicts = conflicts - setOf(Conflict.POOLS, Conflict.POOL_AND_FUND, Conflict.FUNDS))

    private fun Form.withFund(id: String) =
        copy(fundId = id, poolId = null, conflicts = conflicts - setOf(Conflict.POOLS, Conflict.POOL_AND_FUND, Conflict.FUNDS))

    private fun edit(refresh: Boolean = false, change: Form.() -> Form) {
        form.update { it.change().copy(error = null) }
        if (refresh) refreshSuggestions()
        publish()
    }

    private fun refreshSuggestions() {
        val pool = form.value.poolId
        viewModelScope.launch {
            val suggested = suggestDefaults(pool?.let(::PoolId))
            form.update { if (it.poolId == pool) it.copy(suggested = suggested) else it }
            publish()
        }
    }

    private fun submit(confirmNegative: Boolean) {
        val current = form.value
        if (current.submitting) return
        // La UI ya desactiva Guardar; aquí se vuelve a exigir para que una acción suelta no guarde lo sugerido.
        if (!_uiState.value.canSave) return
        val draft = draftOf(current) ?: return
        form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val outcome = attempt { confirm(draft, confirmNegative) }
            when (val error = outcome.errorOrNull()) {
                null -> form.update { it.copy(saved = true, submitting = false) }
                is BusinessError.WouldGoNegative -> form.update { it.copy(negativeWarning = error, submitting = false) }
                else -> form.update { it.copy(error = error, submitting = false) }
            }
            publish()
        }
    }

    private fun draftOf(form: Form): QuickEntryDraft? {
        val view = view(form)
        val amount = MoneyInputFilter.toMinor(form.amountText)?.let { Money(it, CurrencyCode.PEN) } ?: return null
        val account = view.accountId?.let(::AccountId) ?: return null
        val note = form.note.trim().ifEmpty { null }
        if (form.isIncome) {
            val source = form.sourceId?.let(::IncomeSourceId) ?: return null
            return QuickEntryDraft.Income(account, amount, source, note)
        }
        val category = view.categoryId?.let(::CategoryId) ?: return null
        return QuickEntryDraft.Expense(
            account, amount, category, form.tagIds.map(::TagId).toSet(),
            form.poolId?.let(::PoolId), form.fundId?.let(::FundId), note,
        )
    }

    /** Lo explícito de la línea gana; si falta y no hay conflicto, se usa lo sugerido. */
    private data class View(val accountId: String?, val accountSuggested: Boolean, val categoryId: String?, val categorySuggested: Boolean)

    private fun view(form: Form): View {
        val account = form.accountId ?: form.suggested.accountId?.value?.takeIf { Conflict.ACCOUNTS !in form.conflicts }
        val category = form.categoryId ?: form.suggested.categoryId?.value?.takeIf { Conflict.CATEGORIES !in form.conflicts }
        return View(account, form.accountId == null && account != null, category, form.categoryId == null && category != null)
    }

    private fun publish() {
        val form = form.value
        val view = view(form)
        val options = { kind: EntityKind ->
            form.snapshot.entries.filter { it.kind == kind }.map { EntityOption(it.id, it.names.first()) }
        }
        val categoryOptions = options(EntityKind.CATEGORY)
        val tagNames = options(EntityKind.TAG).filter { it.id in form.tagIds || it.id in form.suggested.tagIds.map(TagId::value) && form.poolId != null }
            .map { it.label }
        _uiState.value = QuickEntryUiState(
            line = form.line,
            isIncome = form.isIncome,
            amountText = form.amountText,
            accountId = view.accountId,
            accountSuggested = view.accountSuggested,
            categoryId = view.categoryId.takeIf { !form.isIncome },
            categorySuggested = view.categorySuggested && !form.isIncome,
            tagNames = tagNames,
            poolId = form.poolId,
            fundId = form.fundId,
            sourceId = form.sourceId,
            note = form.note,
            ambiguities = form.ambiguities,
            conflicts = form.conflicts,
            accountOptions = options(EntityKind.ACCOUNT),
            categoryOptions = categoryOptions,
            poolOptions = options(EntityKind.POOL),
            fundOptions = options(EntityKind.FUND),
            sourceOptions = options(EntityKind.INCOME_SOURCE),
            canSave = form.line.isNotBlank() && form.ambiguities.isEmpty() && form.conflicts.isEmpty() &&
                !form.submitting && draftOf(form) != null,
            saveBlock = when {
                form.submitting -> SaveBlock.SAVING
                form.line.isBlank() -> SaveBlock.EMPTY_LINE
                form.ambiguities.isNotEmpty() -> SaveBlock.AMBIGUOUS
                form.conflicts.isNotEmpty() -> SaveBlock.CONFLICT
                draftOf(form) == null -> SaveBlock.MISSING_DATA
                else -> null
            },
            error = form.error,
            negativeWarning = form.negativeWarning,
            saved = form.saved,
        )
    }
}
