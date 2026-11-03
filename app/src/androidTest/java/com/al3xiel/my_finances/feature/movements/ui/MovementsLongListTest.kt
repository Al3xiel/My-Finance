package com.al3xiel.my_finances.feature.movements.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** 5 000 movimientos en 500 días: la lista pagina por claves estables y llega al último sin colgarse. */
class MovementsLongListTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun fiveThousandMovementsScrollToTheEndWithUniqueKeys() {
        val start = LocalDate.of(2026, 10, 26)
        val days = (0 until 500).map { d ->
            val rows = (0 until 10).map { r ->
                val n = d * 10 + r
                MovementRowUi(
                    MovementId("m$n"), MovementType.EXPENSE, "BBVA", null, "Gasto $n",
                    Money(100L + n, CurrencyCode.PEN), false, canCorrect = true, canVoid = true,
                )
            }
            DaySection(start.minusDays(d.toLong()), rows, Money(-rows.sumOf { it.amount.amountMinor }, CurrencyCode.PEN))
        }
        rule.setContent {
            MyfinancesTheme { MovementsContent(MovementsUiState(days = days, loaded = true), {}, {}) }
        }
        // 500 encabezados + 5 000 filas.
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(5_499)
        rule.onNodeWithText("Gasto 4999").assertIsDisplayed()
    }
}
