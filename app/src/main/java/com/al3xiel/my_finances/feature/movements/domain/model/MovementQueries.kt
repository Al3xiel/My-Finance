package com.al3xiel.my_finances.feature.movements.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.ReimbursementStatus
import java.time.Instant
import java.time.LocalDate

/**
 * Todos los campos son opcionales y se combinan con "y". `text` busca en las notas. Con `categoryIds` no vacío (la categoría
 * elegida y sus subcategorías, D37) basta con una parte en cualquiera de ellas; `categoryId` sigue siendo la elegida.
 */
data class MovementFilter(
    val accountId: AccountId? = null,
    val type: MovementType? = null,
    val categoryId: CategoryId? = null,
    val tagId: TagId? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val text: String? = null,
    val categoryIds: Set<CategoryId> = emptySet(),
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

/**
 * Una parte de gasto que va a un cúmulo, con lo que ya se le reembolsó. Solo lectura de la proyección de partes: no hay
 * evento ni tabla nuevos para esto.
 */
data class PartReimbursement(
    val partId: PartId,
    val poolId: PoolId,
    val poolName: String,
    val amount: Money,
    val reimbursed: Money,
    val status: ReimbursementStatus,
) {
    val pending: Money get() = amount - reimbursed
}

data class MovementDetail(
    val movement: Movement,
    val history: List<MovementHistoryEntry>,
    /** Una entrada por cada parte con cúmulo, en el orden de las partes. */
    val reimbursements: List<PartReimbursement> = emptyList(),
)
