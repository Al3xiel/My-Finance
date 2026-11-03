package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.FeeData
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RegisterTransferUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RegisterTransferUseCaseTest {
    private val f = MovementFixture()
    private val register = RegisterTransferUseCase(f.rules, f.accounts, f.recorder, f.ids)

    @Test
    fun emitsTransferRegisteredWithFee() = runTest {
        val fee = FeeData(f.pen(50), f.fees)
        val result = register(f.bbva, f.cash, f.pen(300), fee, "retiro", f.today)

        assertEquals(Outcome.Ok(MovementId("id-1")), result)
        assertEquals(
            TransferRegistered(MovementId("id-1"), f.bbva, f.cash, f.pen(300), fee, null, "retiro"),
            f.store.events.single().event,
        )
    }

    @Test
    fun feeIsOptional() = runTest {
        register(f.bbva, f.cash, f.pen(300), null, null, f.today)
        assertEquals(null, (f.store.events.single().event as TransferRegistered).fee)
    }

    @Test
    fun rejectsInvalidInputWithoutEmitting() = runTest {
        f.accounts.accounts[AccountId("old")] = f.account(AccountId("old"), "Vieja", archived = true)
        fun failed(e: BusinessError) = Outcome.Failed(e)

        assertEquals(failed(BusinessError.SameAccount), register(f.bbva, f.bbva, f.pen(100), null, null, f.today))
        assertEquals(failed(BusinessError.InvalidAmount), register(f.bbva, f.cash, f.pen(0), null, null, f.today))
        assertEquals(failed(BusinessError.FutureDate), register(f.bbva, f.cash, f.pen(100), null, null, f.today.plusDays(1)))
        assertEquals(failed(BusinessError.NotFound), register(f.bbva, AccountId("ghost"), f.pen(100), null, null, f.today))
        assertEquals(failed(BusinessError.AlreadyArchived), register(AccountId("old"), f.cash, f.pen(100), null, null, f.today))
        assertEquals(failed(BusinessError.InvalidAmount), register(f.bbva, f.cash, f.pen(100), FeeData(f.pen(0), f.fees), null, f.today))
        assertEquals(failed(BusinessError.NotFound), register(f.bbva, f.cash, f.pen(100), FeeData(f.pen(5), CategoryId("ghost")), null, f.today))
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun warnsWhenAmountPlusFeeLeavesTheSourceNegative() = runTest {
        val fee = FeeData(f.pen(50), f.fees)
        assertEquals(
            Outcome.Failed(BusinessError.WouldGoNegative(-20)),
            register(f.bbva, f.cash, f.pen(970), fee, null, f.today),
        )
        assertEquals(0, f.store.events.size)

        assertEquals(Outcome.Ok(MovementId("id-1")), register(f.bbva, f.cash, f.pen(970), fee, null, f.today, confirmNegativeBalance = true))
        assertEquals(Outcome.Ok(MovementId("id-3")), register(f.bbva, f.cash, f.pen(950), fee, null, f.today))
    }
}
