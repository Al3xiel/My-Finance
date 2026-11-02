package com.al3xiel.my_finances.feature.reports.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.reports.domain.model.AccountOrigin
import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRecord
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyLink
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyNode
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyNodeKind
import com.al3xiel.my_finances.feature.reports.domain.repository.OriginRepository
import com.al3xiel.my_finances.feature.reports.domain.rules.OriginCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class OriginSliceUi(val sourceId: String, val name: String, val amountMinor: Long, val basisPoints: Int)

data class OriginAccountUi(
    val accountId: String,
    val name: String,
    val balanceMinor: Long,
    val slices: List<OriginSliceUi>,
    val hasShortfall: Boolean,
)

data class OriginSourceOptionUi(val id: String, val name: String, val totalMinor: Long)

/** `categoryName` es `null` en préstamos y ajustes: la pantalla los rotula según `kind`. */
data class SpendRowUi(val accountName: String, val categoryName: String?, val kind: LedgerEntryKind, val amountMinor: Long)

/** `label` es `null` en "Préstamos" y "No identificado": su nombre es un texto de la pantalla. */
data class SankeyBoxUi(val node: SankeyNode, val label: String?, val valueMinor: Long)

data class SankeyUi(val columns: List<List<SankeyBoxUi>> = emptyList(), val links: List<SankeyLink> = emptyList())

data class ReportOriginUiState(
    val period: ReportPeriod,
    val accounts: List<OriginAccountUi> = emptyList(),
    val sourceOptions: List<OriginSourceOptionUi> = emptyList(),
    val selectedSourceId: String? = null,
    val spend: List<SpendRowUi> = emptyList(),
    val sankey: SankeyUi = SankeyUi(),
    val anyShortfall: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface ReportOriginUiAction {
    data class SelectSource(val sourceId: String) : ReportOriginUiAction
}

/** S44: de qué fuente viene el saldo de cada cuenta y de cuál salió el gasto del periodo. Todo es estimado (docs/04 §5.1). */
@HiltViewModel(assistedFactory = ReportOriginViewModel.Factory::class)
class ReportOriginViewModel @AssistedInject constructor(
    @Assisted("from") from: String,
    @Assisted("to") to: String,
    origin: OriginRepository,
    accounts: AccountRepository,
    sources: IncomeSourceRepository,
    categories: CategoryRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(@Assisted("from") from: String, @Assisted("to") to: String): ReportOriginViewModel
    }

    private class Loaded(val composition: List<AccountOrigin>, val records: List<OriginAttributionRecord>)

    private val period = ReportPeriod.fromTexts(from, to)
    private val chosen = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReportOriginUiState> = combine(
        flow { emit(Loaded(origin.composition(), origin.attributions(period))) },
        accounts.observeAccounts(),
        sources.observeAll(),
        categories.observeAll(),
        chosen,
    ) { loaded, accountList, sourceList, categoryList, choice ->
        val accountNames = accountList.associate { it.account.id.value to it.account.name }
        val sourceNames = sourceList.associate { it.id.value to it.name }
        val categoryNames = categoryList.associate { it.id.value to it.name }
        val options = loaded.records.filter { it.amountMinor > 0 }.groupBy { it.sourceId }
            .map { (id, group) -> OriginSourceOptionUi(id, sourceNames[id] ?: id, group.sumOf { it.amountMinor }) }
            .sortedWith(compareByDescending<OriginSourceOptionUi> { it.totalMinor }.thenBy { it.id })
        val selected = choice?.takeIf { id -> options.any { it.id == id } } ?: options.firstOrNull()?.id
        ReportOriginUiState(
            period = period,
            accounts = loaded.composition.map { account ->
                OriginAccountUi(
                    account.accountId, accountNames[account.accountId] ?: account.accountId, account.balanceMinor,
                    account.slices.map { OriginSliceUi(it.sourceId, sourceNames[it.sourceId] ?: it.sourceId, it.amountMinor, it.basisPoints) },
                    account.hasShortfall,
                )
            }.sortedBy { it.name },
            sourceOptions = options,
            selectedSourceId = selected,
            spend = selected?.let { id ->
                OriginCalculator.spendOf(id, loaded.records).map {
                    SpendRowUi(accountNames[it.accountId] ?: it.accountId, it.categoryId?.let { c -> categoryNames[c] ?: c }, it.kind, it.amountMinor)
                }
            }.orEmpty(),
            sankey = sankey(OriginCalculator.sankey(loaded.records), sourceNames, accountNames, categoryNames),
            anyShortfall = loaded.composition.any { it.hasShortfall },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReportOriginUiState(period))

    fun onAction(action: ReportOriginUiAction) {
        when (action) {
            is ReportOriginUiAction.SelectSource -> chosen.value = action.sourceId
        }
    }

    /** Tres columnas (fuentes, cuentas, destinos); cada nodo vale lo que pasa por él, y la tabla del diagrama repite los flujos. */
    private fun sankey(
        links: List<SankeyLink>,
        sourceNames: Map<String, String>,
        accountNames: Map<String, String>,
        categoryNames: Map<String, String>,
    ): SankeyUi {
        fun column(nodes: List<SankeyNode>, valueOf: (SankeyNode) -> Long): List<SankeyBoxUi> = nodes.distinct().map { node ->
            val label = when (node.kind) {
                SankeyNodeKind.SOURCE -> sourceNames[node.id] ?: node.id
                SankeyNodeKind.ACCOUNT -> accountNames[node.id] ?: node.id
                SankeyNodeKind.CATEGORY -> categoryNames[node.id] ?: node.id
                SankeyNodeKind.LOANS, SankeyNodeKind.UNIDENTIFIED -> null
            }
            SankeyBoxUi(node, label, valueOf(node))
        }.sortedWith(compareByDescending<SankeyBoxUi> { it.valueMinor }.thenBy { it.node.kind }.thenBy { it.node.id })

        if (links.isEmpty()) return SankeyUi()
        val intoAccounts = links.filter { it.to.kind == SankeyNodeKind.ACCOUNT }
        val outOfAccounts = links.filter { it.from.kind == SankeyNodeKind.ACCOUNT }
        return SankeyUi(
            columns = listOf(
                column(intoAccounts.map { it.from }) { node -> intoAccounts.filter { it.from == node }.sumOf { it.amountMinor } },
                column(intoAccounts.map { it.to }) { node -> intoAccounts.filter { it.to == node }.sumOf { it.amountMinor } },
                column(outOfAccounts.map { it.to }) { node -> outOfAccounts.filter { it.to == node }.sumOf { it.amountMinor } },
            ),
            links = links,
        )
    }
}
