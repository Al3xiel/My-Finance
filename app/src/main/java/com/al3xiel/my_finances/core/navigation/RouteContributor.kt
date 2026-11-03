package com.al3xiel.my_finances.core.navigation

import androidx.navigation3.runtime.EntryProviderScope

/** A feature registers the screens of its routes here; the shell never knows the features. */
fun interface RouteContributor {
    fun EntryProviderScope<Route>.register()
}
