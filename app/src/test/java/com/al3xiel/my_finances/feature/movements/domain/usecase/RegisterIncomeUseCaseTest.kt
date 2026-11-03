package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RegisterIncomeUseCaseTest {
    private val f = MovementFixture()
    private val register = RegisterIncomeUseCase(f.rules, f.recorder, f.ids)

    @Test
    fun emitsIncomeRegisteredWithoutCategory() = runTest {
        val day = f.today.minusDays(1)
        val result = register(f.bbva, f.pen(5_000), f.source, "sueldo", day)

        assertEquals(Outcome.Ok(MovementId("id-1")), result)
        val stored = f.store.events.single()
        assertEquals(IncomeRegistered(MovementId("id-1"), f.bbva, f.pen(5_000), f.source, null, "sueldo"), stored.event)
        assertEquals(day, stored.occurredOn)
        assertEquals("id-2", stored.correlationId)
    }

    @Test
    fun rejectsInvalidInputWithoutEmitting() = runTest {
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)

        assertEquals(Outcome.Failed(BusinessError.InvalidAmount), register(f.bbva, f.pen(0), f.source, null, f.today))
        assertEquals(Outcome.Failed(BusinessError.FutureDate), register(f.bbva, f.pen(100), f.source, null, f.today.plusDays(1)))
        assertEquals(Outcome.Failed(BusinessError.NotFound), register(AccountId("ghost"), f.pen(100), f.source, null, f.today))
        assertEquals(Outcome.Failed(BusinessError.AlreadyArchived), register(f.cash, f.pen(100), f.source, null, f.today))
        assertEquals(Outcome.Failed(BusinessError.NotFound), register(f.bbva, f.pen(100), IncomeSourceId("ghost"), null, f.today))
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun rejectsAnAmountAboveTheMaximumTheAmountFieldAllows() = runTest {
        val max = com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules.MAX_AMOUNT_MINOR

        assertEquals(Outcome.Failed(BusinessError.InvalidAmount), register(f.bbva, f.pen(max + 1), f.source, null, f.today))
        assertEquals(Outcome.Ok(MovementId("id-1")), register(f.bbva, f.pen(max), f.source, null, f.today))
    }
}
