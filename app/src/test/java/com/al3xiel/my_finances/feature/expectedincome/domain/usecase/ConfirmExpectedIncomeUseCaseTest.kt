package com.al3xiel.my_finances.feature.expectedincome.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeConfirmed
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeScheduled
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConfirmExpectedIncomeUseCaseTest {
    private val f = MovementFixture()
    private val repo = FakeExpectedIncomeRepository()
    private val funds = FakeFundRepository()
    private val confirm = ConfirmExpectedIncomeUseCase(f.rules, repo, funds, f.recorder, f.ids, f.writeLock)

    private val e1 = ExpectedIncomeId("e1")
    private val fare = FundId("fare")

    private fun failed(error: BusinessError) = Outcome.Failed(error)

    @Test
    fun confirmsInOneCorrelationAndSchedulesTheNextWhenItRepeats() = runTest {
        repo.put("e1", f.bbva, f.source, repeats = true)

        val result = confirm(e1, f.bbva, f.pen(205_000), f.source, "Sueldo", f.today)

        assertEquals(Outcome.Ok(MovementId("id-1")), result)
        assertEquals(
            listOf(
                IncomeRegistered(MovementId("id-1"), f.bbva, f.pen(205_000), f.source, null, "Sueldo"),
                ExpectedIncomeConfirmed(e1, MovementId("id-1"), ExpectedIncomeId("id-2")),
                // El siguiente sale del programado (28 oct–5 nov), no del cobro real.
                ExpectedIncomeScheduled(
                    ExpectedIncomeId("id-2"), f.source, f.bbva, f.pen(200_000), LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 5), true,
                ),
            ),
            f.store.events.map { it.event },
        )
        assertEquals(1, f.store.events.map { it.correlationId }.toSet().size)
    }

    @Test
    fun aOneOffExpectedIncomeGeneratesNoNext() = runTest {
        repo.put("e1", f.bbva, f.source, repeats = false)

        confirm(e1, f.bbva, f.pen(200_000), f.source, null, f.today)

        assertEquals(
            listOf(IncomeRegistered(MovementId("id-1"), f.bbva, f.pen(200_000), f.source, null, null), ExpectedIncomeConfirmed(e1, MovementId("id-1"), null)),
            f.store.events.map { it.event },
        )
    }

    @Test
    fun theOptionalTopUpGoesBetweenTheIncomeAndTheConfirmation() = runTest {
        repo.put("e1", f.bbva, f.source)
        funds.put(fare)

        confirm(e1, f.bbva, f.pen(200_000), f.source, null, f.today, fare, f.pen(50_000))

        assertEquals(
            listOf("IncomeRegistered", "FundToppedUp", "ExpectedIncomeConfirmed"),
            f.store.events.map { it.event.type },
        )
        assertEquals(FundToppedUp(fare, f.pen(50_000), MovementId("id-1")), f.store.events[1].event)
    }

    @Test
    fun theTopUpIsValidatedLikeInAnyIncome() = runTest {
        repo.put("e1", f.bbva, f.source)

        assertEquals(failed(BusinessError.NotFound), confirm(e1, f.bbva, f.pen(100), f.source, null, f.today, fare, f.pen(50)))
        funds.put(fare, status = FundStatus.CLOSED)
        assertEquals(failed(BusinessError.FundClosed), confirm(e1, f.bbva, f.pen(100), f.source, null, f.today, fare, f.pen(50)))
        funds.put(fare)
        assertEquals(failed(BusinessError.ExceedsIncome), confirm(e1, f.bbva, f.pen(100), f.source, null, f.today, fare, f.pen(101)))
        assertEquals(failed(BusinessError.InvalidAmount), confirm(e1, f.bbva, f.pen(100), f.source, null, f.today, fare, null))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun onlyAPendingOneCanBeConfirmed() = runTest {
        repo.put("e1", f.bbva, f.source, status = ExpectedIncomeStatus.CONFIRMED)
        repo.put("e2", f.bbva, f.source, status = ExpectedIncomeStatus.CANCELLED)
        repo.put("e3", f.bbva, f.source, status = ExpectedIncomeStatus.OVERDUE)

        assertEquals(failed(BusinessError.NotPending), confirm(e1, f.bbva, f.pen(100), f.source, null, f.today))
        assertEquals(failed(BusinessError.NotPending), confirm(ExpectedIncomeId("e2"), f.bbva, f.pen(100), f.source, null, f.today))
        assertEquals(failed(BusinessError.NotFound), confirm(ExpectedIncomeId("zz"), f.bbva, f.pen(100), f.source, null, f.today))
        assertTrue(f.store.events.isEmpty())

        assertEquals(Outcome.Ok(MovementId("id-1")), confirm(ExpectedIncomeId("e3"), f.bbva, f.pen(100), f.source, null, f.today))
    }

    @Test
    fun anArchivedAccountOfTheExpectedIncomeMustBeEditedFirst() = runTest {
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)
        repo.put("e1", f.cash, f.source)

        // Aunque el ingreso real vaya a otra cuenta activa.
        assertEquals(failed(BusinessError.AlreadyArchived), confirm(e1, f.bbva, f.pen(100), f.source, null, f.today))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun theIncomeItselfIsValidated() = runTest {
        repo.put("e1", f.bbva, f.source)

        assertEquals(failed(BusinessError.InvalidAmount), confirm(e1, f.bbva, f.pen(0), f.source, null, f.today))
        assertEquals(failed(BusinessError.FutureDate), confirm(e1, f.bbva, f.pen(100), f.source, null, f.today.plusDays(1)))
        assertTrue(f.store.events.isEmpty())
    }
}
