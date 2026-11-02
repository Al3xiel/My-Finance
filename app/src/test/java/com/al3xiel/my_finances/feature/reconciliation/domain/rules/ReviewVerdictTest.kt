package com.al3xiel.my_finances.feature.reconciliation.domain.rules

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.event.AdjustmentData
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import org.junit.Assert.assertEquals
import org.junit.Test

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
private fun income(minor: Long) = AdjustmentData(MovementId("m9"), AdjustmentDirection.UNIDENTIFIED_INCOME, pen(minor))

class ReviewVerdictTest {
    @Test
    fun balancedStillAddingUpIsFine() {
        assertEquals(ReviewVerdict.OK, ReviewVerdict.of(pen(0), null))
    }

    @Test
    fun balancedWithADifferenceNowNeedsReview() {
        assertEquals(ReviewVerdict.NEEDS_REVIEW, ReviewVerdict.of(pen(100), null))
        assertEquals(ReviewVerdict.NEEDS_REVIEW, ReviewVerdict.of(pen(-100), null))
    }

    @Test
    fun adjustmentThatStillMatchesTheDifferenceIsFine() {
        assertEquals(ReviewVerdict.OK, ReviewVerdict.of(pen(240), income(240)))
    }

    @Test
    fun adjustmentWithAnotherAmountOrDirectionNeedsReview() {
        assertEquals(ReviewVerdict.NEEDS_REVIEW, ReviewVerdict.of(pen(300), income(240)))
        assertEquals(ReviewVerdict.NEEDS_REVIEW, ReviewVerdict.of(pen(-240), income(240)))
        assertEquals(ReviewVerdict.NEEDS_REVIEW, ReviewVerdict.of(pen(0), income(240)))
    }
}
