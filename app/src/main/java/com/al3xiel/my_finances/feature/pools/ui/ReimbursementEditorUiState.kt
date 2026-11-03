package com.al3xiel.my_finances.feature.pools.ui

import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.feature.pools.domain.model.PendingPartLine
import java.time.LocalDate

/** Una parte pendiente con lo que saldaría el reembolso y, en modo manual, el monto tecleado. */
data class ReimbursementLineUi(
    val line: PendingPartLine,
    val willSettle: Money,
    val manualText: String,
)

data class ReimbursementEditorUiState(
    val loaded: Boolean = false,
    val personId: PersonId? = null,
    /** Cúmulo de origen si se abrió desde él: limita `OLDEST_FIRST` a ese cúmulo (D18). */
    val poolId: PoolId? = null,
    val personOptions: List<EntityOption> = emptyList(),
    val accountOptions: List<EntityOption> = emptyList(),
    val accountId: AccountId? = null,
    val amountText: String = "",
    val strategy: AllocationStrategy = AllocationStrategy.OLDEST_FIRST,
    val occurredOn: LocalDate = LocalDate.ofEpochDay(0),
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val note: String = "",
    val lines: List<ReimbursementLineUi> = emptyList(),
    val pendingTotal: Money = Money.zero(CurrencyCode.PEN),
    /** D1: el monto no puede ser mayor a lo pendiente. */
    val exceedsPending: Boolean = false,
    /** Modo manual: lo que falta repartir (o sobra, si es negativo) entre las partes. */
    val unassigned: Money = Money.zero(CurrencyCode.PEN),
    val error: BusinessError? = null,
    val saved: Boolean = false,
)

sealed interface ReimbursementEditorUiAction {
    data class SelectPerson(val id: PersonId) : ReimbursementEditorUiAction
    data class SelectAccount(val id: AccountId) : ReimbursementEditorUiAction
    data class EditAmount(val text: String) : ReimbursementEditorUiAction
    data class SelectStrategy(val strategy: AllocationStrategy) : ReimbursementEditorUiAction
    data class EditManual(val partId: PartId, val text: String) : ReimbursementEditorUiAction
    data class SelectDate(val date: LocalDate) : ReimbursementEditorUiAction
    data class EditNote(val text: String) : ReimbursementEditorUiAction
    data object Save : ReimbursementEditorUiAction
}
