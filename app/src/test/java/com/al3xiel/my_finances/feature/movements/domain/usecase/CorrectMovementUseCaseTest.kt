package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CorrectMovementUseCaseTest {
    private val f = MovementFixture()
    private val correct = CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock)
    private val m1 = MovementId("m1")

    private fun failed(error: BusinessError) = Outcome.Failed(error)

    private fun expenseDraft(vararg parts: ExpensePartInput, total: Long = parts.sumOf { it.amount.amountMinor }) =
        MovementDraft.Expense(f.bbva, f.pen(total), parts.toList(), "nota", f.today.minusDays(1))

    @Test
    fun correctedExpenseKeepsExistingPartIdsAndGivesNewOnesToNewParts() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 400, "p2" to 100)
        val draft = expenseDraft(f.input(300, partId = PartId("p1")), f.input(50))

        assertEquals(Outcome.Ok(Unit), correct(m1, draft, "  monto mal  "))

        val stored = f.store.events.single()
        assertEquals(draft.occurredOn, stored.occurredOn)
        assertEquals(
            MovementCorrected(
                m1,
                MovementSnapshot.Expense(
                    f.bbva, f.pen(350),
                    listOf(
                        ExpensePartData(PartId("p1"), f.pen(300), f.food, emptyList(), null, null),
                        ExpensePartData(PartId("id-1"), f.pen(50), f.food, emptyList(), null, null),
                    ),
                    "nota",
                ),
                "monto mal", null,
            ),
            stored.event,
        )
    }

    @Test
    fun correctedIncome() = runTest {
        f.incomeMovement("m1", f.bbva, 500)
        val draft = MovementDraft.Income(f.cash, f.pen(700), f.source, null, f.today)

        assertEquals(Outcome.Ok(Unit), correct(m1, draft, "cuenta equivocada"))
        assertEquals(
            MovementSnapshot.Income(f.cash, f.pen(700), f.source, null),
            (f.store.events.single().event as MovementCorrected).snapshot,
        )
    }

    @Test
    fun requiresAReasonAndAnExistingActiveNonTransferMovementOfTheSameType() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 100)
        f.transferMovement("tr")
        f.expenseMovement("v", f.bbva, "p9" to 100, status = MovementStatus.VOIDED)
        val draft = expenseDraft(f.input(100, partId = PartId("p1")))

        assertEquals(failed(BusinessError.ReasonRequired), correct(m1, draft, "   "))
        assertEquals(failed(BusinessError.NotFound), correct(MovementId("ghost"), draft, "x"))
        assertEquals(failed(BusinessError.TransferNotEditable), correct(MovementId("tr"), draft, "x"))
        assertEquals(failed(BusinessError.AlreadyVoided), correct(MovementId("v"), draft, "x"))
        assertEquals(
            failed(BusinessError.TypeChangeNotAllowed),
            correct(m1, MovementDraft.Income(f.bbva, f.pen(100), f.source, null, f.today), "x"),
        )
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun validatesTheNewContent() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 100)

        assertEquals(failed(BusinessError.InvalidPart), correct(m1, expenseDraft(f.input(100, partId = PartId("other"))), "x"))
        assertEquals(
            failed(BusinessError.InvalidPart),
            correct(m1, expenseDraft(f.input(50, partId = PartId("p1")), f.input(50, partId = PartId("p1"))), "x"),
        )
        assertEquals(failed(BusinessError.PartsMismatch), correct(m1, expenseDraft(f.input(50, partId = PartId("p1")), total = 100), "x"))
        assertEquals(failed(BusinessError.NoParts), correct(m1, expenseDraft(total = 100), "x"))
        assertEquals(
            failed(BusinessError.FutureDate),
            correct(m1, expenseDraft(f.input(100)).copy(occurredOn = f.today.plusDays(1)), "x"),
        )
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun warnsOnlyWhenTheCorrectionMakesTheBalanceWorseAndNegative() = runTest {
        // BBVA = 1000 incluye este gasto de 400; subirlo a 1500 deja el saldo en 1000 + 400 - 1500 = -100.
        f.expenseMovement("m1", f.bbva, "p1" to 400)
        val worse = expenseDraft(f.input(1_500, partId = PartId("p1")))
        assertEquals(failed(BusinessError.WouldGoNegative(-100)), correct(m1, worse, "x"))
        assertEquals(Outcome.Ok(Unit), correct(m1, worse, "x", confirmNegativeBalance = true))

        // Con el saldo ya negativo, bajar el gasto no avisa.
        f.accounts.balances[f.bbva] = -300
        assertEquals(Outcome.Ok(Unit), correct(m1, expenseDraft(f.input(200, partId = PartId("p1"))), "x"))
    }

    @Test
    fun correctingOnlyTheNoteStillWorksWhenTheCategoryWasArchivedAfterwards() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 100)
        f.categories.categories[f.food] = f.categories.categories.getValue(f.food).copy(isArchived = true)

        val sameCategory = expenseDraft(f.input(100, partId = PartId("p1")))
        assertEquals(Outcome.Ok(Unit), correct(m1, sameCategory, "nota mal"))

        // Cambiar a una categoría archivada que el gasto no tenía sí se rechaza.
        f.categories.categories[f.fees] = f.categories.categories.getValue(f.fees).copy(isArchived = true)
        val archivedNew = expenseDraft(f.input(100, partId = PartId("p1"), category = f.fees))
        assertEquals(failed(BusinessError.AlreadyArchived), correct(m1, archivedNew, "x"))
    }

    @Test
    fun correctingAMovementOfAnArchivedAccountIsAllowedButMovingItToOneIsNot() = runTest {
        f.incomeMovement("m1", f.cash, 100)
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)

        assertEquals(Outcome.Ok(Unit), correct(m1, MovementDraft.Income(f.cash, f.pen(120), f.source, null, f.today), "monto"))
        f.accounts.accounts[f.bbva] = f.account(f.bbva, "BBVA", archived = true)
        assertEquals(
            failed(BusinessError.AlreadyArchived),
            correct(m1, MovementDraft.Income(f.bbva, f.pen(120), f.source, null, f.today), "cuenta"),
        )
    }

    @Test
    fun anArchivedCategoryIsOnlyAcceptedInThePartThatAlreadyHadIt() = runTest {
        val base = f.expenseMovement("m1", f.bbva, "p1" to 100, "p2" to 100)
        f.movements.movements[base.id] = base.copy(parts = listOf(base.parts[0], base.parts[1].copy(categoryId = f.fees)))
        f.categories.categories[f.food] = f.categories.categories.getValue(f.food).copy(isArchived = true)

        // p1 conserva su categoría archivada: se acepta.
        val keeps = expenseDraft(f.input(100, partId = PartId("p1")), f.input(100, partId = PartId("p2"), category = f.fees))
        assertEquals(Outcome.Ok(Unit), correct(m1, keeps, "nota"))

        // p2 recibe la categoría archivada de p1: se rechaza.
        val moved = expenseDraft(f.input(100, partId = PartId("p1")), f.input(100, partId = PartId("p2"), category = f.food))
        assertEquals(failed(BusinessError.AlreadyArchived), correct(m1, moved, "x"))

        // Una parte nueva con esa categoría tampoco.
        val added = expenseDraft(f.input(100, partId = PartId("p1")), f.input(100, partId = PartId("p2"), category = f.fees), f.input(50))
        assertEquals(failed(BusinessError.AlreadyArchived), correct(m1, added, "x"))
    }
}
