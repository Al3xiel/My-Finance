package com.al3xiel.my_finances.core.designsystem

import androidx.annotation.DrawableRes
import com.al3xiel.my_finances.R

/**
 * Índice único de iconos: vectores de Material Symbols Rounded, solo los usados (D69).
 * Quien necesite uno nuevo lo agrega aquí y en `res/drawable`, no importa `material-icons-extended`.
 */
object AppIcons {
    @DrawableRes val Home = R.drawable.ic_tab_home
    @DrawableRes val Movements = R.drawable.ic_tab_movements
    @DrawableRes val Receivables = R.drawable.ic_tab_receivables
    @DrawableRes val Reports = R.drawable.ic_tab_reports
    @DrawableRes val More = R.drawable.ic_tab_more
    @DrawableRes val Add = R.drawable.ic_add
    @DrawableRes val Visibility = R.drawable.ic_visibility
    @DrawableRes val VisibilityOff = R.drawable.ic_visibility_off
    @DrawableRes val Back = R.drawable.ic_arrow_back
    @DrawableRes val StatusPending = R.drawable.ic_status_pending
    @DrawableRes val StatusOverdue = R.drawable.ic_status_overdue
    @DrawableRes val StatusReview = R.drawable.ic_status_review
    @DrawableRes val StatusReconciled = R.drawable.ic_status_reconciled
    @DrawableRes val StatusEstimated = R.drawable.ic_status_estimated

    /** Nombre del recurso → id; el test comprueba que cada uno existe en `res/drawable`. */
    val all: Map<String, Int> = mapOf(
        "ic_tab_home" to Home,
        "ic_tab_movements" to Movements,
        "ic_tab_receivables" to Receivables,
        "ic_tab_reports" to Reports,
        "ic_tab_more" to More,
        "ic_add" to Add,
        "ic_visibility" to Visibility,
        "ic_visibility_off" to VisibilityOff,
        "ic_arrow_back" to Back,
        "ic_status_pending" to StatusPending,
        "ic_status_overdue" to StatusOverdue,
        "ic_status_review" to StatusReview,
        "ic_status_reconciled" to StatusReconciled,
        "ic_status_estimated" to StatusEstimated,
    )
}
