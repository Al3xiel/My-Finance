package com.al3xiel.my_finances.feature.movements.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.TagId
import java.time.Instant
import java.time.LocalDate

/** Todos los campos son opcionales y se combinan con "y". `text` busca en las notas. */
data class MovementFilter(
    val accountId: AccountId? = null,
    val type: MovementType? = null,
    val categoryId: CategoryId? = null,
    val tagId: TagId? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val text: String? = null,
)

/** Fila de la lista: lo justo para pintar un movimiento sin cargar sus partes. */
data class MovementSummary(
    val id: MovementId,
    val type: MovementType,
    val accountId: AccountId,
    val counterAccountId: AccountId?,
    val amount: Money,
    val note: String?,
    val occurredOn: LocalDate,
    val status: MovementStatus,
    val revision: Int,
)

enum class HistoryKind { REGISTERED, CORRECTED, VOIDED }

data class MovementHistoryEntry(
    val sequence: Long,
    val kind: HistoryKind,
    val occurredOn: LocalDate,
    val recordedAt: Instant,
    val reason: String?,
)

data class MovementDetail(val movement: Movement, val history: List<MovementHistoryEntry>)
