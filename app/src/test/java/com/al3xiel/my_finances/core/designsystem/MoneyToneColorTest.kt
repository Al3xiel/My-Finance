package com.al3xiel.my_finances.core.designsystem

import androidx.compose.ui.graphics.Color
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.ui.theme.FinanceColors
import org.junit.Assert.assertEquals
import org.junit.Test

/** Con "ocultar montos" el color no puede contar lo que el texto esconde: un saldo negativo no se delata en rojo. */
class MoneyToneColorTest {
    private val finance = FinanceColors.Light
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    @Test fun `a visible negative amount with the automatic tone is red`() {
        assertEquals(finance.expense, moneyToneColor(MoneyTone.AUTO, pen(-500), hidden = false, finance))
    }

    @Test fun `a hidden negative amount keeps the neutral color`() {
        assertEquals(Color.Unspecified, moneyToneColor(MoneyTone.AUTO, pen(-500), hidden = true, finance))
    }

    @Test fun `a hidden amount ignores any tone`() {
        listOf(MoneyTone.INCOME, MoneyTone.EXPENSE, MoneyTone.AUTO).forEach {
            assertEquals(Color.Unspecified, moneyToneColor(it, pen(-500), hidden = true, finance))
        }
    }

    @Test fun `visible tones keep their colors and a positive automatic tone is neutral`() {
        assertEquals(finance.income, moneyToneColor(MoneyTone.INCOME, pen(500), hidden = false, finance))
        assertEquals(finance.expense, moneyToneColor(MoneyTone.EXPENSE, pen(500), hidden = false, finance))
        assertEquals(Color.Unspecified, moneyToneColor(MoneyTone.AUTO, pen(500), hidden = false, finance))
        assertEquals(Color.Unspecified, moneyToneColor(MoneyTone.NONE, pen(-500), hidden = false, finance))
    }
}
