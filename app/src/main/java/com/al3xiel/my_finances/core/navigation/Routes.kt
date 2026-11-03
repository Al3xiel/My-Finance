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

// Onboarding feature (first use, S02–S05): its own flow, without tabs or FAB
@Serializable sealed interface Onboarding : Route {
    @Serializable data object Welcome : Onboarding
    @Serializable data object Accounts : Onboarding
    @Serializable data object Security : Onboarding
    @Serializable data object Backup : Onboarding
}

// Backup feature
@Serializable data object Backup : Route

// Movements feature (ids travel as text: a value class cannot be a route argument)
@Serializable data class MovementDetail(val id: String) : Route
/** La lista de Movimientos abierta desde un reporte (D37): fechas ISO e ids como texto; el ViewModel arma el filtro. */
@Serializable data class MovementsFiltered(
    val from: String? = null,
    val to: String? = null,
    val categoryId: String? = null,
    val tagId: String? = null,
    val accountId: String? = null,
) : Route
@Serializable data class ExpenseEditor(
    val id: String? = null,
    val presetPoolId: String? = null,
    val presetFundId: String? = null,
    /** Abierto desde la conciliación S61: el gasto (o su corrección) queda vinculado a ella. */
    val reconciliationId: String? = null,
) : Route
/** `expectedIncomeId` abre el ingreso prellenado desde un ingreso esperado (S55 → S23); con `id` se corrige un ingreso. */
@Serializable data class IncomeEditor(
    val id: String? = null,
    val expectedIncomeId: String? = null,
    val reconciliationId: String? = null,
) : Route

// Accounts feature
@Serializable data object Accounts : Route
@Serializable data class AccountDetail(val id: String) : Route
@Serializable data class TransferEditor(val reconciliationId: String? = null) : Route
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

// Reports feature (the period travels as ISO dates: a route cannot carry a sealed period)
@Serializable data class ReportByCategory(val from: String, val to: String) : Route
@Serializable data class ReportByTag(val from: String, val to: String) : Route
@Serializable data class ReportByAccount(val from: String, val to: String) : Route
@Serializable data class ReportOrigin(val from: String, val to: String) : Route
@Serializable data class ReportExport(val from: String, val to: String) : Route

// Funds (ids travel as text)
@Serializable data object Funds : Route
@Serializable data class FundDetail(val id: String) : Route
@Serializable data class FundCount(val id: String) : Route
@Serializable data class FundEditor(val id: String? = null) : Route

// Reconciliation (ids travel as text)
@Serializable data class ReconciliationStart(val accountId: String) : Route
@Serializable data class ReconciliationDetail(val reconciliationId: String) : Route
@Serializable data class ReconciliationResolve(val reconciliationId: String) : Route

// Expected incomes (ids travel as text)
@Serializable data object ExpectedIncomes : Route
@Serializable data class ExpectedIncomeEditor(val id: String? = null) : Route

// Diagnostics (ids travel as text). One workspace holds one package, so S64 needs no id; S65 carries the reconciliation the
// package came from, because a dictionary can only be exported from it.
@Serializable data object DevTools : Route
@Serializable data class DiagnosticExport(val reconciliationId: String? = null) : Route
@Serializable data object DiagnosticWorkspace : Route
@Serializable data class DictionaryExport(val reconciliationId: String) : Route
