package com.al3xiel.my_finances.core.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.al3xiel.my_finances.R

enum class MainTab(
    val root: Route,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val showsRegisterFab: Boolean,
) {
    HOME(Home, R.string.tab_home, R.drawable.ic_tab_home, true),
    MOVEMENTS(Movements, R.string.tab_movements, R.drawable.ic_tab_movements, true),
    RECEIVABLES(Receivables, R.string.tab_receivables, R.drawable.ic_tab_receivables, true),
    REPORTS(Reports, R.string.tab_reports, R.drawable.ic_tab_reports, false),
    MORE(More, R.string.tab_more, R.drawable.ic_tab_more, false);

    companion object {
        fun fromRoot(route: Route): MainTab? = entries.firstOrNull { it.root == route }
    }
}
