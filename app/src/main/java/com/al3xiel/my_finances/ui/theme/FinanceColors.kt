package com.al3xiel.my_finances.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores con significado de finanzas. Cada uno trae contenedor y `on-`; el estado nunca se comunica solo con color
 * (siempre lo acompaña texto o icono). El ingreso es un verde bien separado del turquesa.
 */
@Immutable
data class FinanceColors(
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val estimated: Color,
    val onEstimated: Color,
    val estimatedContainer: Color,
    val onEstimatedContainer: Color,
    val neutral: Color,
    val onNeutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
) {
    companion object {
        val Light = FinanceColors(
            income = Color(0xFF1B6E2E), onIncome = Color(0xFFFFFFFF),
            incomeContainer = Color(0xFFC8F0C9), onIncomeContainer = Color(0xFF00210A),
            expense = Color(0xFFB3261E), onExpense = Color(0xFFFFFFFF),
            expenseContainer = Color(0xFFFFDAD6), onExpenseContainer = Color(0xFF410002),
            warning = Color(0xFF7A4B00), onWarning = Color(0xFFFFFFFF),
            warningContainer = Color(0xFFFFDDB0), onWarningContainer = Color(0xFF2A1700),
            estimated = Color(0xFF4F4FA3), onEstimated = Color(0xFFFFFFFF),
            estimatedContainer = Color(0xFFE1E0FF), onEstimatedContainer = Color(0xFF0D0B5E),
            neutral = Color(0xFF5C6666), onNeutral = Color(0xFFFFFFFF),
            neutralContainer = Color(0xFFDEE5E4), onNeutralContainer = Color(0xFF171D1D),
        )

        val Dark = FinanceColors(
            income = Color(0xFF7DDC8A), onIncome = Color(0xFF003915),
            incomeContainer = Color(0xFF00531F), onIncomeContainer = Color(0xFFC8F0C9),
            expense = Color(0xFFFFB4AB), onExpense = Color(0xFF690005),
            expenseContainer = Color(0xFF93000A), onExpenseContainer = Color(0xFFFFDAD6),
            warning = Color(0xFFFFB95C), onWarning = Color(0xFF452B00),
            warningContainer = Color(0xFF653E00), onWarningContainer = Color(0xFFFFDDB0),
            estimated = Color(0xFFC0C1FF), onEstimated = Color(0xFF1F1F78),
            estimatedContainer = Color(0xFF36368B), onEstimatedContainer = Color(0xFFE1E0FF),
            neutral = Color(0xFFB9C4C3), onNeutral = Color(0xFF283231),
            neutralContainer = Color(0xFF3F4948), onNeutralContainer = Color(0xFFDEE4E3),
        )
    }
}

val LocalFinanceColors = staticCompositionLocalOf { FinanceColors.Light }
