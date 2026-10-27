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

// Movements feature (ids travel as text: a value class cannot be a route argument)
@Serializable data class MovementDetail(val id: String) : Route
@Serializable data class ExpenseEditor(val id: String? = null, val presetPoolId: String? = null) : Route
@Serializable data class IncomeEditor(val id: String? = null) : Route

// Accounts feature
@Serializable data object Accounts : Route
@Serializable data class AccountDetail(val id: String) : Route
@Serializable data object TransferEditor : Route
@Serializable data object IncomeSources : Route

// Catalogs
@Serializable data object Categories : Route
@Serializable data object Tags : Route
@Serializable data object People : Route

// Pools, reimbursements, people and loans (ids travel as text)
@Serializable data class PoolDetail(val id: String) : Route
@Serializable data class PoolEditor(val id: String? = null) : Route
@Serializable data class SettlementStatement(val poolId: String) : Route
@Serializable data class ReimbursementEditor(val personId: String? = null, val poolId: String? = null) : Route
@Serializable data class PersonDetail(val id: String) : Route
@Serializable data class LoanEditor(val personId: String? = null) : Route
@Serializable data class LoanDetail(val id: String) : Route
