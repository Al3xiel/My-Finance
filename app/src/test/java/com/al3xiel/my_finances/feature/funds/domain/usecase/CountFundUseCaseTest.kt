package com.al3xiel.my_finances.feature.funds.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.FundCounted
import com.al3xiel.my_finances.domain.model.event.UntrackedSpendData
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountFundUseCaseTest {
    private val fx = MovementFixture()
    private val funds = FakeFundRepository()
    private val count = CountFundUseCase(funds, fx.rules, fx.recorder, fx.ids, fx.writeLock)

    private val fare = FundId("fare")
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private fun failed(error: BusinessError) = Outcome.Failed(error)

    @Test
    fun chargesWhatIsMissingAsUntrackedSpendWithAFreshMovementId() = runTest {
        // asignado 50,00 − detallado 5,00 − ya cargado 2,00 − declarado 38,00 = 5,00
        funds.put(fare, allocated = 5_000, detailed = 500, untracked = 200)

        val result = count(fare, pen(3_800), fx.cash, fx.food)

        assertEquals(Outcome.Ok(pen(500)), result)
        assertEquals(
            FundCounted(fare, pen(3_800), UntrackedSpendData(MovementId("id-1"), fx.cash, fx.food, pen(500))),
            fx.store.events.single().event,
        )
        assertEquals(fx.today, fx.store.events.single().occurredOn)
    }

    @Test
    fun whenNothingIsMissingItNeedsNoAccountNorCategory() = runTest {
        funds.put(fare, allocated = 5_000, detailed = 500)

        val result = count(fare, pen(4_500), null, null)

        assertEquals(Outcome.Ok(pen(0)), result)
        assertEquals(FundCounted(fare, pen(4_500), null), fx.store.events.single().event)
    }

    @Test
    fun declaringMoreThanWhatIsLeftIsRejected() = runTest {
        funds.put(fare, allocated = 5_000, detailed = 500)

        assertEquals(failed(BusinessError.DeclaredAboveRemaining), count(fare, pen(4_501), fx.cash, fx.food))
        assertEquals(failed(BusinessError.NegativeAmount), count(fare, pen(-1), fx.cash, fx.food))
        assertTrue(fx.store.events.isEmpty())
    }

    @Test
    fun untrackedSpendNeedsAnActiveAccountAndCategory() = runTest {
        funds.put(fare, allocated = 5_000)

        assertEquals(failed(BusinessError.NotFound), count(fare, pen(4_000), null, fx.food))
        assertEquals(failed(BusinessError.NotFound), count(fare, pen(4_000), fx.cash, null))
        assertEquals(failed(BusinessError.NotFound), count(fare, pen(4_000), com.al3xiel.my_finances.domain.model.AccountId("ghost"), fx.food))
        assertTrue(fx.store.events.isEmpty())
    }

    @Test
    fun missingAndClosedFundsAreRejected() = runTest {
        assertEquals(failed(BusinessError.NotFound), count(fare, pen(0), fx.cash, fx.food))

        funds.put(fare, allocated = 5_000, status = FundStatus.CLOSED)
        assertEquals(failed(BusinessError.FundClosed), count(fare, pen(0), fx.cash, fx.food))
        assertTrue(fx.store.events.isEmpty())
    }
}
