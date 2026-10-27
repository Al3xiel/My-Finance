package com.al3xiel.my_finances.core.navigation

import androidx.annotation.StringRes
import javax.inject.Inject

data class MoreMenuEntry(
    val id: String,
    @StringRes val labelRes: Int,
    val route: Route,
    val order: Int,
)

fun interface MoreMenuContributor {
    fun entries(): List<MoreMenuEntry>
}

class MoreMenuRegistry @Inject constructor(
    contributors: Set<@JvmSuppressWildcards MoreMenuContributor>,
) {
    val entries: List<MoreMenuEntry> = contributors
        .flatMap { it.entries() }
        .sortedWith(compareBy({ it.order }, { it.id }))
}
