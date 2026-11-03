package com.al3xiel.my_finances.feature.movements.ui.components

import androidx.annotation.StringRes
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.domain.model.ReimbursementStatus

/** Tabla de estados (docs/05 §7): pendiente y parcial esperan; reembolsada ya está. */
internal fun ReimbursementStatus.badge(): BadgeStatus = when (this) {
    ReimbursementStatus.PENDING, ReimbursementStatus.PARTIALLY_REIMBURSED -> BadgeStatus.PENDING
    ReimbursementStatus.REIMBURSED -> BadgeStatus.RECONCILED
}

@StringRes
internal fun ReimbursementStatus.labelRes(): Int = when (this) {
    ReimbursementStatus.PENDING -> R.string.status_pending
    ReimbursementStatus.PARTIALLY_REIMBURSED -> R.string.status_partial
    ReimbursementStatus.REIMBURSED -> R.string.status_reimbursed
}
