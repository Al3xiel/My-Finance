package com.al3xiel.my_finances.data.projection.reconciliation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus

/**
 * Sin columnas calculadas: la diferencia restante se consulta al vuelo (D30). `adjustment_minor` lleva signo (ingreso +,
 * egreso −) para recordar la dirección; `snapshot_computed_minor` y el ajuste son la evidencia de la re-verificación (D31).
 */
@Entity(tableName = "reconciliations", indices = [Index("account_id", "status")])
data class ReconciliationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    val currency: CurrencyCode,
    @ColumnInfo(name = "real_minor") val realMinor: Long,
    @ColumnInfo(name = "snapshot_computed_minor") val snapshotComputedMinor: Long,
    @ColumnInfo(name = "checked_sequence") val checkedSequence: Long,
    val status: ReconciliationStatus,
    val cause: ReconciliationCause?,
    @ColumnInfo(name = "adjustment_movement_id") val adjustmentMovementId: String?,
    @ColumnInfo(name = "adjustment_minor") val adjustmentMinor: Long?,
    @ColumnInfo(name = "cancel_reason") val cancelReason: String?,
    @ColumnInfo(name = "checked_at") val checkedAt: String,
    @ColumnInfo(name = "resolved_at") val resolvedAt: String?,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean,
)

@Entity(
    tableName = "reconciliation_links",
    primaryKeys = ["reconciliation_id", "movement_id"],
    indices = [Index("movement_id")],
)
data class ReconciliationLinkEntity(
    @ColumnInfo(name = "reconciliation_id") val reconciliationId: String,
    @ColumnInfo(name = "movement_id") val movementId: String,
)
