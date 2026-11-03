package com.al3xiel.my_finances.core.designsystem

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MoneyTextTest {
    private val money = Money(123_456, CurrencyCode.PEN)

    @Test
    fun showsTheFormattedAmountWhenNotHidden() {
        assertEquals("S/ 1,234.56", moneyDisplayText(money, hidden = false))
    }

    @Test
    fun hidingMasksEveryDigit() {
        val text = moneyDisplayText(money, hidden = true)
        assertEquals("S/ ••••", text)
        assertFalse(text.any { it.isDigit() })
    }

    @Test
    fun signsAreAppliedToBothVisibleAndMaskedAmounts() {
        assertEquals("+S/ 1,234.56", moneyDisplayText(money, hidden = false, sign = MoneySign.PLUS))
        assertEquals("−S/ 1,234.56", moneyDisplayText(money, hidden = false, sign = MoneySign.MINUS))
        assertEquals("−S/ ••••", moneyDisplayText(money, hidden = true, sign = MoneySign.MINUS))
    }

    @Test
    fun negativeBalancesKeepTheirOwnMinus() {
        assertEquals("-S/ 3.00", moneyDisplayText(Money(-300, CurrencyCode.PEN), hidden = false))
    }
}
