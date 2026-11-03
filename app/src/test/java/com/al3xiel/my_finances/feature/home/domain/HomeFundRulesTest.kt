package com.al3xiel.my_finances.feature.home.domain

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.home.domain.rules.HomeFundRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFundRulesTest {
    private val funds = FakeFundRepository()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    @Test
    fun aDetailedFundShowsWhatWasSpentAndIsNotEstimated() {
        val fund = funds.put(FundId("a"), allocated = 10_000, detailed = 3_000, untracked = 1_000)

        assertEquals(pen(4_000), HomeFundRules.consumed(fund))
        assertFalse(HomeFundRules.isEstimated(fund))
    }

    @Test
    fun aDailyEstimateFundShowsTheEstimateAndSaysSo() {
        val fund = funds.put(FundId("b"), mode = ConsumptionMode.DAILY_ESTIMATE, dailyEstimate = 500, allocated = 10_000, usageDays = 4)

        assertEquals(pen(2_000), HomeFundRules.consumed(fund))
        assertTrue(HomeFundRules.isEstimated(fund))
    }

    @Test
    fun theFractionIsClampedBetweenZeroAndOne() {
        assertEquals(0f, HomeFundRules.fraction(pen(100), pen(0)), 0f)
        assertEquals(0.25f, HomeFundRules.fraction(pen(25), pen(100)), 0.0001f)
        assertEquals(1f, HomeFundRules.fraction(pen(300), pen(100)), 0f)
    }
}
