package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.TagId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainEventSerializationTest {
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private val part = ExpensePartData(PartId("p1"), pen(150), CategoryId("c1"), listOf(TagId("t1")), null, null)

    private val samples: List<DomainEvent> = listOf(
        AccountCreated(AccountId("a1"), "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(100_000)),
        AccountRenamed(AccountId("a1"), "BBVA Sueldo"),
        AccountArchived(AccountId("a1")),
        CategoryCreated(CategoryId("c1"), "Comida", "restaurant", "#FF0000", null),
        CategoryRenamed(CategoryId("c1"), "Alimentos"),
        CategoryArchived(CategoryId("c1")),
        TagCreated(TagId("t1"), "Mamá", TagKind.PERSON, PersonId("pe1")),
        TagRenamed(TagId("t1"), "Mami"),
        TagArchived(TagId("t1")),
        PersonCreated(PersonId("pe1"), "Mamá"),
        PersonRenamed(PersonId("pe1"), "Mami"),
        PersonArchived(PersonId("pe1")),
        IncomeSourceCreated(IncomeSourceId("s1"), "Sueldo", null),
        IncomeRegistered(MovementId("m1"), AccountId("a1"), pen(5_000), IncomeSourceId("s1"), ReconciliationId("r1"), "nota"),
        ExpenseRegistered(MovementId("m2"), AccountId("a1"), pen(150), listOf(part), null, null),
        TransferRegistered(MovementId("m3"), AccountId("a1"), AccountId("a2"), pen(2_000), FeeData(pen(50), CategoryId("c2")), null, null),
        MovementCorrected(MovementId("m2"), MovementSnapshot.Expense(AccountId("a1"), pen(150), listOf(part), null), "monto mal", null),
        MovementCorrected(MovementId("m1"), MovementSnapshot.Income(AccountId("a1"), pen(5_000), IncomeSourceId("s1"), null), "fuente", null),
        MovementVoided(MovementId("m2"), "duplicado", null),
        ProjectionsRebuilt(mapOf("catalog" to 1), "replay"),
    )

    @Test
    fun everyEventRoundTrips() {
        samples.forEach { event ->
            val text = EventCodec.encode(event)
            assertEquals(event, EventCodec.decode(event.type, event.schemaVersion, text))
        }
    }

    @Test
    fun encodingIsDeterministic() {
        samples.forEach { assertEquals(EventCodec.encode(it), EventCodec.encode(it)) }
    }

    @Test
    fun samplesCoverEveryRegisteredType() {
        assertEquals(EventCodec.types, samples.map { it.type }.toSet())
    }

    @Test
    fun incomePayloadHasNoCategoryAndKeepsReconciliationId() {
        val text = EventCodec.encode(samples.filterIsInstance<IncomeRegistered>().single())
        assertTrue("categoryId" !in text)
        assertTrue("reconciliationId" in text)
    }

    @Test
    fun unknownTypeFails() {
        assertThrows(IllegalStateException::class.java) { EventCodec.decode("Nope", 1, "{}") }
    }

    @Test
    fun aggregateIdsComeFromTheEntity() {
        assertEquals("m1", samples.filterIsInstance<IncomeRegistered>().single().aggregateId)
        assertEquals(AggregateType.ACCOUNT, samples.first().aggregateType)
    }
}
