package com.al3xiel.my_finances.feature.diagnostics.domain.anonymize

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EntityRef
import com.al3xiel.my_finances.domain.model.event.EntityType
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.model.event.PersonRenamed
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventAnonymizerTest {
    private val pen = CurrencyCode.PEN
    private val day = LocalDate.of(2026, 10, 27)
    private val anonymizer = EventAnonymizer()

    private fun stored(vararg events: DomainEvent): List<StoredEvent> = runBlocking {
        val store = FakeEventStore()
        store.append("c", events.map { NewEvent(it, day) })
        store.readAll()
    }

    private fun account(id: String, name: String) = AccountCreated(AccountId(id), name, AccountType.BANK, pen, "#112233", Money(10_000, pen))

    private fun payloadOf(result: AnonymizationResult, sequence: Long) = result.events.first { it.source.sequence == sequence }.payload

    @Test
    fun accountsGetLetterPseudonymsInOrderOfAppearance() {
        val result = anonymizer.anonymize(stored(account("a1", "BBVA Sueldo"), account("a2", "Efectivo")))

        assertTrue(payloadOf(result, 1).contains("\"name\":\"Account-A\""))
        assertTrue(payloadOf(result, 2).contains("\"name\":\"Account-B\""))
    }

    @Test
    fun aRenamedEntityKeepsItsPseudonym() {
        val result = anonymizer.anonymize(stored(account("a1", "BBVA"), AccountRenamed(AccountId("a1"), "BBVA Pareja")))

        assertTrue(payloadOf(result, 2).contains("\"name\":\"Account-A\""))
        assertEquals(mapOf("Account-A" to EntityRef(EntityType.ACCOUNT, "a1")), result.pseudonymMap)
    }

    @Test
    fun twoEntitiesWithTheSameAliasNeverShareAPseudonym() {
        val result = anonymizer.anonymize(stored(PersonCreated(PersonId("p1"), "Ana"), PersonCreated(PersonId("p2"), "Ana")))

        assertEquals(setOf("Person-1", "Person-2"), result.pseudonymMap.keys)
    }

    @Test
    fun peopleCategoriesAndAccountsUseTheirOwnNumbering() {
        val result = anonymizer.anonymize(
            stored(
                PersonCreated(PersonId("p1"), "Ana"),
                CategoryCreated(CategoryId("c1"), "Comida", "icon", "#fff", null),
            ),
        )

        assertEquals(
            mapOf("Person-1" to EntityRef(EntityType.PERSON, "p1"), "Category-1" to EntityRef(EntityType.CATEGORY, "c1")),
            result.pseudonymMap,
        )
    }

    @Test
    fun noAliasSurvivesInAnyPayload() {
        val result = anonymizer.anonymize(
            stored(
                account("a1", "BBVA Sueldo"),
                PersonCreated(PersonId("p1"), "Ana Gómez"),
                PersonRenamed(PersonId("p1"), "Anita"),
            ),
        )

        val all = result.events.joinToString { it.payload }
        listOf("BBVA", "Ana", "Gómez", "Anita").forEach { assertFalse(it, all.contains(it)) }
    }

    @Test
    fun requiredFreeTextBecomesRemovedAndOptionalTextBecomesNull() {
        val result = anonymizer.anonymize(
            stored(
                IncomeRegistered(MovementId("m1"), AccountId("a1"), Money(500, pen), IncomeSourceId("s1"), null, "sueldo de ana"),
                MovementVoided(MovementId("m1"), "me equivoqué con Ana", null),
            ),
        )

        assertTrue(payloadOf(result, 1).contains("\"note\":null"))
        assertTrue(payloadOf(result, 2).contains("\"reason\":\"[removed]\""))
    }

    @Test
    fun aNoteInsideACorrectionSnapshotIsRemovedToo() {
        val snapshot = MovementSnapshot.Income(AccountId("a1"), Money(700, pen), IncomeSourceId("s1"), "nota privada")
        val result = anonymizer.anonymize(stored(MovementCorrected(MovementId("m1"), snapshot, "motivo privado", null)))

        val payload = payloadOf(result, 1)
        assertFalse(payload.contains("privad"))
        assertTrue(payload.contains("\"note\":null"))
        assertTrue(payload.contains("\"reason\":\"[removed]\""))
    }

    @Test
    fun notesInsideExpensePartsListsAreRemoved() {
        val part = ExpensePartData(PartId("x1"), Money(300, pen), CategoryId("c1"), emptyList(), null, null)
        val result = anonymizer.anonymize(stored(ExpenseRegistered(MovementId("m1"), AccountId("a1"), Money(300, pen), listOf(part), null, "cena con ana")))

        assertFalse(payloadOf(result, 1).contains("cena"))
    }

    @Test
    fun aPartDetailIsRemovedFromTheExportedDiagnostic() {
        val parts = listOf(
            ExpensePartData(PartId("x1"), Money(300, pen), CategoryId("c1"), emptyList(), null, null, "Pastillas de Boby"),
            ExpensePartData(PartId("x2"), Money(200, pen), CategoryId("c1"), emptyList(), null, null, null),
        )
        val result = anonymizer.anonymize(stored(ExpenseRegistered(MovementId("m1"), AccountId("a1"), Money(500, pen), parts, null, null)))

        val payload = payloadOf(result, 1)
        assertFalse(payload.contains("Boby"))
        assertFalse(payload.contains("Pastillas"))
        assertTrue(result.altered.isNotEmpty())
    }

    @Test
    fun aPartDetailInsideACorrectionSnapshotIsRemovedToo() {
        val parts = listOf(ExpensePartData(PartId("x1"), Money(300, pen), CategoryId("c1"), emptyList(), null, null, "Pastillas de Boby"))
        val snapshot = MovementSnapshot.Expense(AccountId("a1"), Money(300, pen), parts, null)
        val result = anonymizer.anonymize(stored(MovementCorrected(MovementId("m1"), snapshot, "monto mal", null)))

        assertFalse(payloadOf(result, 1).contains("Boby"))
    }

    @Test
    fun amountsDatesIdsAndEnumsTravelUntouched() {
        val events = stored(account("a1", "BBVA"))
        val result = anonymizer.anonymize(events)
        val payload = payloadOf(result, 1)

        listOf("\"accountId\":\"a1\"", "\"amountMinor\":10000", "\"currency\":\"PEN\"", "\"accountType\":\"BANK\"", "\"color\":\"#112233\"").forEach {
            assertTrue(it, payload.contains(it))
        }
        assertEquals(events.single().sequence, result.events.single().source.sequence)
        assertEquals(events.single().hash, result.events.single().source.hash)
    }

    @Test
    fun anEventWithoutTextIsLeftExactlyAsStoredAndIsNotListedAsAltered() {
        val voidWithoutText = IncomeRegistered(MovementId("m1"), AccountId("a1"), Money(500, pen), IncomeSourceId("s1"), null, null)
        val events = stored(voidWithoutText)
        val result = anonymizer.anonymize(events)

        assertEquals(events.single().payload, result.events.single().payload)
        assertTrue(result.altered.isEmpty())
    }

    @Test
    fun alteredEventsKeepTheirOriginalHashAndTheFieldsThatChanged() {
        val events = stored(account("a1", "BBVA"), IncomeRegistered(MovementId("m1"), AccountId("a1"), Money(1, pen), IncomeSourceId("s1"), null, "x"))
        val result = anonymizer.anonymize(events)

        assertEquals(listOf(1L, 2L), result.altered.map { it.sequence })
        assertEquals(events.map { it.hash }, result.altered.map { it.originalHash })
        assertEquals(listOf("AccountCreated.name"), result.altered[0].fields)
        assertEquals(listOf("IncomeRegistered.note"), result.altered[1].fields)
    }

    @Test
    fun sameInputGivesSameOutput() {
        val events = stored(account("a1", "BBVA"), PersonCreated(PersonId("p1"), "Ana"), account("a2", "Caja"))

        val first = anonymizer.anonymize(events)
        val second = EventAnonymizer().anonymize(events)

        assertEquals(first.events.map { it.payload }, second.events.map { it.payload })
        assertEquals(first.pseudonymMap.toList(), second.pseudonymMap.toList())
    }

    @Test
    fun theRewrittenPayloadIsStillValidJsonForItsType() {
        val result = anonymizer.anonymize(stored(account("a1", "BBVA")))
        val event = result.events.single()

        EventCodec.decode(event.source.event.type, event.source.schemaVersion, event.payload)
    }

    @Test
    fun manyAccountsKeepGettingDistinctLetters() {
        val many = (1..30).map { account("a$it", "n$it") }
        val keys = anonymizer.anonymize(stored(*many.toTypedArray())).pseudonymMap.keys.toList()

        assertEquals(30, keys.toSet().size)
        assertEquals("Account-A", keys.first())
        assertEquals("Account-Z", keys[25])
        assertEquals("Account-AA", keys[26])
    }
}
