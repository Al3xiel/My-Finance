package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RegisterExpenseUseCaseTest {
    private val f = MovementFixture()
    private val register = RegisterExpenseUseCase(f.rules, f.accounts, f.recorder, f.ids)

    private fun failed(error: BusinessError) = Outcome.Failed(error)

    @Test
    fun emitsExpenseRegisteredWithGeneratedIdsAndTheGivenDate() = runTest {
        val day = f.today.minusDays(2)
        val result = register(f.bbva, f.pen(300), listOf(f.input(200, tags = listOf(f.tag)), f.input(100)), "mercado", day)

        assertEquals(Outcome.Ok(MovementId("id-1")), result)
        val stored = f.store.events.single()
        assertEquals(day, stored.occurredOn)
        assertEquals(
            ExpenseRegistered(
                MovementId("id-1"), f.bbva, f.pen(300),
                listOf(
                    ExpensePartData(PartId("id-2"), f.pen(200), f.food, listOf(f.tag), null, null),
                    ExpensePartData(PartId("id-3"), f.pen(100), f.food, emptyList(), null, null),
                ),
                null, "mercado",
            ),
            stored.event,
        )
        assertEquals("id-4", stored.correlationId)
    }

    @Test
    fun todayIsAllowedButFutureIsNot() = runTest {
        assertEquals(Outcome.Ok(MovementId("id-1")), register(f.bbva, f.pen(100), listOf(f.input(100)), null, f.today))
        assertEquals(failed(BusinessError.FutureDate), register(f.bbva, f.pen(100), listOf(f.input(100)), null, f.today.plusDays(1)))
    }

    @Test
    fun rejectsInvalidInputWithoutEmitting() = runTest {
        val one = listOf(f.input(100))
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)
        f.categories.categories[CategoryId("old")] =
            Category(CategoryId("old"), "Vieja", null, true)

        assertEquals(failed(BusinessError.InvalidAmount), register(f.bbva, f.pen(0), one, null, f.today))
        assertEquals(failed(BusinessError.UnsupportedCurrency), register(f.bbva, Money(100, CurrencyCode.USD), one, null, f.today))
        assertEquals(failed(BusinessError.NotFound), register(AccountId("ghost"), f.pen(100), one, null, f.today))
        assertEquals(failed(BusinessError.AlreadyArchived), register(f.cash, f.pen(100), one, null, f.today))
        assertEquals(failed(BusinessError.NoParts), register(f.bbva, f.pen(100), emptyList(), null, f.today))
        assertEquals(failed(BusinessError.PartsMismatch), register(f.bbva, f.pen(100), listOf(f.input(60)), null, f.today))
        assertEquals(failed(BusinessError.InvalidAmount), register(f.bbva, f.pen(100), listOf(f.input(150), f.input(-50)), null, f.today))
        assertEquals(failed(BusinessError.NotFound), register(f.bbva, f.pen(100), listOf(f.input(100, category = CategoryId("ghost"))), null, f.today))
        assertEquals(failed(BusinessError.AlreadyArchived), register(f.bbva, f.pen(100), listOf(f.input(100, category = CategoryId("old"))), null, f.today))
        assertEquals(failed(BusinessError.NotFound), register(f.bbva, f.pen(100), listOf(f.input(100, tags = listOf(TagId("ghost")))), null, f.today))
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun warnsBeforeLeavingTheBalanceNegativeAndSavesWhenConfirmed() = runTest {
        val parts = listOf(f.input(1_500))
        assertEquals(failed(BusinessError.WouldGoNegative(-500)), register(f.bbva, f.pen(1_500), parts, null, f.today))
        assertEquals(0, f.store.events.size)

        assertEquals(Outcome.Ok(MovementId("id-1")), register(f.bbva, f.pen(1_500), parts, null, f.today, confirmNegativeBalance = true))
        assertEquals(1, f.store.events.size)
    }

    @Test
    fun spendingExactlyTheBalanceDoesNotWarn() = runTest {
        assertEquals(Outcome.Ok(MovementId("id-1")), register(f.bbva, f.pen(1_000), listOf(f.input(1_000)), null, f.today))
    }

    @Test
    fun hugePartsCannotOverflowTheSumToFakeAMatchingTotal() = runTest {
        val huge = Long.MAX_VALUE
        // Partes cercanas a Long.MAX con un total pequeño: antes la suma desbordaba y podía "cuadrar".
        val parts = listOf(f.input(huge), f.input(huge), f.input(3))
        assertEquals(failed(BusinessError.InvalidAmount), register(f.bbva, f.pen(1), parts, null, f.today))
        assertEquals(0, f.store.events.size)
    }
}
