package com.al3xiel.my_finances.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Sealed interfaces need every subtype in the same module and package: each feature adds its routes here.
@Serializable
sealed interface Route : NavKey

// Tab roots
@Serializable data object Home : Route
@Serializable data object Movements : Route
@Serializable data object Receivables : Route
@Serializable data object Reports : Route
@Serializable data object More : Route

// Privacy feature
@Serializable data object Privacy : Route
@Serializable data object Settings : Route
