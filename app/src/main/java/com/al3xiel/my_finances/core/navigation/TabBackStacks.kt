package com.al3xiel.my_finances.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data class TabBackStacks(
    val selected: MainTab = MainTab.HOME,
    private val stacks: Map<MainTab, List<Route>> = MainTab.entries.associateWith { listOf(it.root) },
) {
    val activeStack: List<Route> get() = stackOf(selected)

    fun stackOf(tab: MainTab): List<Route> = stacks[tab] ?: listOf(tab.root)

    fun select(tab: MainTab): TabBackStacks = copy(selected = tab)

    fun push(route: Route): TabBackStacks =
        copy(stacks = stacks + (selected to (activeStack + route)))

    /** The new state after Back, or null when Back should leave the app. */
    fun back(): TabBackStacks? = when {
        activeStack.size > 1 -> copy(stacks = stacks + (selected to activeStack.dropLast(1)))
        selected != MainTab.HOME -> copy(selected = MainTab.HOME)
        else -> null
    }
}
