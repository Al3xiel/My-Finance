package com.al3xiel.my_finances.feature.funds.domain.rules

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FundExpenseGuardTest {
    private val funds = FakeFundRepository()
    private val guard = FundExpenseGuard(funds)
    private val fare = FundId("fare")
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)

    @Test
    fun anActiveFundAcceptsADateInsideItsRange() = runTest {
        funds.put(fare, startsOn = oct(1), endsOn = oct(31))

        assertNull(guard.check(fare, oct(1), unchanged = false))
        assertNull(guard.check(fare, oct(31), unchanged = false))
    }

    @Test
    fun rejectsMissingClosedAndOutOfRange() = runTest {
        assertEquals(BusinessError.NotFound, guard.check(fare, oct(5), unchanged = false))

        funds.put(fare, startsOn = oct(10), endsOn = oct(20))
        assertEquals(BusinessError.DateOutOfFundRange, guard.check(fare, oct(9), unchanged = false))
        assertEquals(BusinessError.DateOutOfFundRange, guard.check(fare, oct(21), unchanged = false))

        funds.put(fare, status = FundStatus.CLOSED)
        assertEquals(BusinessError.FundClosed, guard.check(fare, oct(5), unchanged = false))
    }

    @Test
    fun theLastCountIsStrictButTheSameDayIsAccepted() = runTest {
        funds.put(fare, lastCountOn = oct(10))

        assertEquals(BusinessError.DateBeforeLastCount, guard.check(fare, oct(9), unchanged = false))
        assertNull(guard.check(fare, oct(10), unchanged = false))
    }

    @Test
    fun anUnchangedPartKeepsItsFundEvenAfterTheFundClosedOrCounted() = runTest {
        funds.put(fare, status = FundStatus.CLOSED, lastCountOn = oct(10))

        assertNull(guard.check(fare, oct(5), unchanged = true))
    }

    @Test
    fun anUnchangedPartWithAMissingFundIsStillNotFound() = runTest {
        assertEquals(BusinessError.NotFound, guard.check(fare, oct(5), unchanged = true))
    }
}
