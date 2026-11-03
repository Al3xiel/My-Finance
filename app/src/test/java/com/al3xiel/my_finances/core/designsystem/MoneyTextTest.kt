package com.al3xiel.my_finances.core.designsystem

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun everyVariantUsesTabularFigures() {
        MoneyVariant.entries.forEach { assertEquals("tnum", it.style().fontFeatureSettings) }
        assertEquals("tnum", androidx.compose.ui.text.TextStyle.Default.tabular().fontFeatureSettings)
    }

    @Test
    fun theHeroIsBiggerThanNormalAndNormalThanSmall() {
        assertTrue(MoneyVariant.HERO.style().fontSize.value > MoneyVariant.NORMAL.style().fontSize.value)
        assertTrue(MoneyVariant.NORMAL.style().fontSize.value > MoneyVariant.SMALL.style().fontSize.value)
    }

    @Test
    fun autoToneIsExpenseOnlyForNegativeAmounts() {
        assertEquals(MoneyTone.EXPENSE, MoneyTone.AUTO.resolve(Money(-1, CurrencyCode.PEN)))
        assertEquals(MoneyTone.NONE, MoneyTone.AUTO.resolve(Money(0, CurrencyCode.PEN)))
        assertEquals(MoneyTone.NONE, MoneyTone.AUTO.resolve(Money(5, CurrencyCode.PEN)))
        assertEquals(MoneyTone.INCOME, MoneyTone.INCOME.resolve(Money(-1, CurrencyCode.PEN)))
    }
}
