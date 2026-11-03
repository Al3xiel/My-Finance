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
    )
}
