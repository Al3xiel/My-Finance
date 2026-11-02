package com.al3xiel.my_finances.feature.reconciliation.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import java.time.Instant
import java.time.LocalDate

/** El ajuste no identificado que dejó una resolución con diferencia. */
data class AdjustmentInfo(val movementId: MovementId, val direction: AdjustmentDirection, val amount: Money)

/** `real` puede ser 0 o negativo (D34); `snapshotComputed` es solo evidencia, la diferencia viva se consulta aparte. */
data class Reconciliation(
    val id: ReconciliationId,
    val accountId: AccountId,
    val real: Money,
    val snapshotComputed: Money,
    val status: ReconciliationStatus,
    val cause: ReconciliationCause?,
    val adjustment: AdjustmentInfo?,
    val cancelReason: String?,
    val checkedAt: Instant,
    val resolvedAt: Instant?,
    val needsReview: Boolean,
)

/** Un movimiento visto desde la conciliación: vinculado o sugerido. */
data class ReconciliationMovement(
    val id: MovementId,
    val type: MovementType,
    val accountId: AccountId,
    val counterAccountId: AccountId?,
    val amount: Money,
    val occurredOn: LocalDate,
    val note: String?,
    val status: MovementStatus,
)

/** Lo que muestra S61: el saldo de la app al momento y la diferencia restante se recalculan con cada cambio. */
data class ReconciliationDetail(
    val reconciliation: Reconciliation,
    val computed: Money,
    val difference: Money,
    val linked: List<ReconciliationMovement>,
    val suggestions: List<ReconciliationMovement>,
)
