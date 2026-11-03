package com.al3xiel.my_finances.domain.model

/** Catálogo que la app siembra al primer arranque (D10). Los ids son fijos para que el sembrado sea idempotente. */
object SystemCatalog {
    data class SystemIncomeSource(val id: IncomeSourceId, val name: String, val kind: IncomeSourceKind)

    data class DefaultCategory(val id: CategoryId, val name: String, val icon: String, val color: String)

    val incomeSources: List<SystemIncomeSource> = listOf(
        SystemIncomeSource(IncomeSourceId(fixedId(1, 1)), "Saldo inicial", IncomeSourceKind.OPENING_BALANCE),
        SystemIncomeSource(IncomeSourceId(fixedId(1, 2)), "Ajuste no identificado", IncomeSourceKind.UNKNOWN_ADJUSTMENT),
        SystemIncomeSource(IncomeSourceId(fixedId(1, 3)), "Reembolso", IncomeSourceKind.REIMBURSEMENT),
        SystemIncomeSource(IncomeSourceId(fixedId(1, 4)), "Pago de préstamo", IncomeSourceKind.LOAN_REPAYMENT),
    )

    /** Categoría de las comisiones de transferencia (D11). */
    val feeCategoryId = CategoryId(fixedId(2, 9))

    val defaultCategories: List<DefaultCategory> = listOf(
        DefaultCategory(CategoryId(fixedId(2, 1)), "Comida", "restaurant", "#E57373"),
        DefaultCategory(CategoryId(fixedId(2, 2)), "Transporte", "directions_bus", "#64B5F6"),
        DefaultCategory(CategoryId(fixedId(2, 3)), "Hogar", "home", "#81C784"),
        DefaultCategory(CategoryId(fixedId(2, 4)), "Salud", "health_and_safety", "#4DB6AC"),
        DefaultCategory(CategoryId(fixedId(2, 5)), "Educación", "school", "#9575CD"),
        DefaultCategory(CategoryId(fixedId(2, 6)), "Ocio", "celebration", "#FFB74D"),
        DefaultCategory(CategoryId(fixedId(2, 7)), "Ropa", "checkroom", "#F06292"),
        DefaultCategory(CategoryId(fixedId(2, 8)), "Servicios", "receipt_long", "#7986CB"),
        DefaultCategory(feeCategoryId, "Comisiones", "account_balance", "#90A4AE"),
        DefaultCategory(CategoryId(fixedId(2, 10)), "Otros", "category", "#A1887F"),
    )

    private val sourceKinds = incomeSources.associate { it.id.value to it.kind }

    fun incomeSourceKind(id: IncomeSourceId): IncomeSourceKind = sourceKinds[id.value] ?: IncomeSourceKind.REGULAR

    private fun fixedId(group: Int, n: Int) = "00000000-0000-4000-8000-%04d%08d".format(group, n)
}
