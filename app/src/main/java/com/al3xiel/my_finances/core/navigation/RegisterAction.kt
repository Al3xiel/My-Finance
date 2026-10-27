package com.al3xiel.my_finances.core.navigation

import androidx.annotation.StringRes
import javax.inject.Inject

data class RegisterAction(
    val id: String,
    @StringRes val labelRes: Int,
    val route: Route,
    val order: Int,
)

fun interface RegisterActionContributor {
    fun actions(): List<RegisterAction>
}

class RegisterActionRegistry @Inject constructor(
    contributors: Set<@JvmSuppressWildcards RegisterActionContributor>,
) {
    val actions: List<RegisterAction> = contributors
        .flatMap { it.actions() }
        .sortedWith(compareBy({ it.order }, { it.id }))

    val hasActions: Boolean get() = actions.isNotEmpty()
}
