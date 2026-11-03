package com.al3xiel.my_finances.feature.loans.ui.components

import androidx.annotation.StringRes
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus

/** Tabla de estados (docs/05 §7): abierta y parcial esperan; pagada ya está. */
fun LoanStatus.badge(): BadgeStatus = when (this) {
    LoanStatus.OPEN -> BadgeStatus.PENDING
    LoanStatus.REPAID -> BadgeStatus.RECONCILED
}

/** El texto del estado de una deuda; "parcial" solo existe mientras sigue abierta y ya se pagó algo. */
@StringRes
fun loanStatusLabelRes(status: LoanStatus, partlyRepaid: Boolean): Int = when (status) {
    LoanStatus.OPEN -> if (partlyRepaid) R.string.status_partial else R.string.loan_detail_status_open
    LoanStatus.REPAID -> R.string.loan_detail_status_repaid
}
