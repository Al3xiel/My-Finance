package com.al3xiel.my_finances.feature.funds.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.FundClosed
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CloseFundUseCaseTest {
    private val store = FakeEventStore()
    private val recorder = EventRecorder(store, SequentialIdGenerator(), Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC))
    private val funds = FakeFundRepository()
    private val close = CloseFundUseCase(funds, recorder, MovementWriteLock())

    private val fare = FundId("fare")
    private val food = FundId("food")
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private fun failed(error: BusinessError) = Outcome.Failed(error)

    @Test
    fun releasingTheRemainderNeedsNoDestination() = runTest {
        funds.put(fare, allocated = 5_000, detailed = 1_200)

        val result = close(fare, RemainderPolicy.RELEASE_TO_AVAILABLE, null)

        assertEquals(Outcome.Ok(Unit), result)
        assertEquals(FundClosed(fare, RemainderPolicy.RELEASE_TO_AVAILABLE, null, null), store.events.single().event)
    }

    @Test
    fun releasingIgnoresADestinationThatWasPassed() = runTest {
        funds.put(fare, allocated = 5_000)
        funds.put(food)

        close(fare, RemainderPolicy.RELEASE_TO_AVAILABLE, food)

        assertEquals(FundClosed(fare, RemainderPolicy.RELEASE_TO_AVAILABLE, null, null), store.events.single().event)
    }

    @Test
    fun rollingOverStoresTheComputedRemainderAndTheDestination() = runTest {
        // sobrante = asignado 50,00 − detallado 12,00 − no registrado 3,00
        funds.put(fare, allocated = 5_000, detailed = 1_200, untracked = 300)
        funds.put(food)

        val result = close(fare, RemainderPolicy.ROLL_OVER, food)

        assertEquals(Outcome.Ok(Unit), result)
        assertEquals(FundClosed(fare, RemainderPolicy.ROLL_OVER, food, pen(3_500)), store.events.single().event)
    }

    @Test
    fun rollingOverAFundThatWasOverspentMovesZero() = runTest {
        funds.put(fare, allocated = 1_000, detailed = 1_500)
        funds.put(food)

        close(fare, RemainderPolicy.ROLL_OVER, food)

        assertEquals(FundClosed(fare, RemainderPolicy.ROLL_OVER, food, pen(0)), store.events.single().event)
    }

    @Test
    fun rollingOverNeedsAnotherActiveFund() = runTest {
        funds.put(fare, allocated = 5_000)

        assertEquals(failed(BusinessError.NotFound), close(fare, RemainderPolicy.ROLL_OVER, null))
        assertEquals(failed(BusinessError.NotFound), close(fare, RemainderPolicy.ROLL_OVER, fare))
        assertEquals(failed(BusinessError.NotFound), close(fare, RemainderPolicy.ROLL_OVER, food))

        funds.put(food, status = FundStatus.CLOSED)
        assertEquals(failed(BusinessError.FundClosed), close(fare, RemainderPolicy.ROLL_OVER, food))
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun missingAndAlreadyClosedFundsAreRejected() = runTest {
        assertEquals(failed(BusinessError.NotFound), close(fare, RemainderPolicy.RELEASE_TO_AVAILABLE, null))

        funds.put(fare, status = FundStatus.CLOSED)
        assertEquals(failed(BusinessError.FundClosed), close(fare, RemainderPolicy.RELEASE_TO_AVAILABLE, null))
        assertTrue(store.events.isEmpty())
    }
}
