package com.al3xiel.my_finances.core.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.AppIcons

enum class MainTab(
    val root: Route,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val showsRegisterFab: Boolean,
) {
    HOME(Home, R.string.tab_home, AppIcons.Home, true),
    MOVEMENTS(Movements, R.string.tab_movements, AppIcons.Movements, true),
    RECEIVABLES(Receivables, R.string.tab_receivables, AppIcons.Receivables, true),
    REPORTS(Reports, R.string.tab_reports, AppIcons.Reports, false),
    MORE(More, R.string.tab_more, AppIcons.More, false);

    companion object {
        fun fromRoot(route: Route): MainTab? = entries.firstOrNull { it.root == route }
    }
}
