package com.al3xiel.my_finances.data.projection.catalog

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.AccountArchived
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.CategoryArchived
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.CategoryRenamed
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.PersonArchived
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.model.event.PersonRenamed
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.TagArchived
import com.al3xiel.my_finances.domain.model.event.TagCreated
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.domain.model.event.TagRenamed
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CatalogProjectorTest {
    private val dao = FakeCatalogDao()
    private val projector = CatalogProjector(dao)
    private val day = LocalDate.of(2026, 10, 25)

    private fun stored(sequence: Long, event: DomainEvent, on: LocalDate = day) = StoredEvent(
        sequence = sequence, eventId = "e$sequence", schemaVersion = 1, correlationId = "c",
        payload = EventCodec.encode(event), occurredOn = on, recordedAt = Instant.ofEpochMilli(sequence),
        appVersion = "test", previousHash = "p", hash = "h", event = event,
    )

    private val events = listOf(
        AccountCreated(AccountId("a1"), "BBVA", AccountType.BANK, CurrencyCode.PEN, "#112233", Money(10_000, CurrencyCode.PEN)),
        AccountRenamed(AccountId("a1"), "BBVA Sueldo"),
        CategoryCreated(CategoryId("c1"), "Comida", "restaurant", "#FF0000", null),
        CategoryCreated(CategoryId("c2"), "Restaurantes", "dining", "#FF8800", CategoryId("c1")),
        CategoryRenamed(CategoryId("c2"), "Salidas"),
        PersonCreated(PersonId("p1"), "Mamá"),
        PersonRenamed(PersonId("p1"), "Mami"),
        TagCreated(TagId("t1"), "Mami", TagKind.PERSON, PersonId("p1")),
        TagCreated(TagId("t2"), "Viaje", TagKind.TOPIC, null),
        TagRenamed(TagId("t2"), "Viajes"),
        IncomeSourceCreated(IncomeSourceId("s1"), "Sueldo", null),
        TagArchived(TagId("t1")),
        CategoryArchived(CategoryId("c1")),
        PersonArchived(PersonId("p1")),
        AccountArchived(AccountId("a1")),
    )

    private suspend fun applyAll(target: CatalogProjector = projector) =
        events.forEachIndexed { i, e -> target.apply(stored(i + 1L, e, day.plusDays(i.toLong()))) }

    @Test
    fun createdEventsProjectRowsWithEventDateAsCreatedOn() = runTest {
        projector.apply(stored(1, events[0], LocalDate.of(2026, 10, 20)))
        val account = dao.accounts.getValue("a1")
        assertEquals("BBVA", account.name)
        assertEquals(AccountType.BANK, account.type)
        assertEquals("2026-10-20", account.createdOn)
        assertEquals("#112233", account.color)
        assertEquals(false, account.isArchived)
    }

    @Test
    fun renamesAndArchivesKeepTheRestOfTheRow() = runTest {
        applyAll()
        assertEquals("BBVA Sueldo", dao.accounts.getValue("a1").name)
        assertTrue(dao.accounts.getValue("a1").isArchived)
        assertEquals("Salidas", dao.categories.getValue("c2").name)
        assertEquals("c1", dao.categories.getValue("c2").parentId)
        assertEquals("dining", dao.categories.getValue("c2").icon)
        assertTrue(dao.categories.getValue("c1").isArchived)
        assertEquals("Mami", dao.people.getValue("p1").alias)
        assertTrue(dao.people.getValue("p1").isArchived)
        assertEquals("Viajes", dao.tags.getValue("t2").name)
        assertEquals("p1", dao.tags.getValue("t1").personId)
        assertEquals(TagKind.PERSON, dao.tags.getValue("t1").kind)
        assertTrue(dao.tags.getValue("t1").isArchived)
    }

    @Test
    fun incomeSourceIsRegularUnlessSystemSeeded() = runTest {
        applyAll()
        assertEquals(IncomeSourceKind.REGULAR, dao.incomeSources.getValue("s1").kind)

        val system = SystemCatalog.incomeSources.first { it.kind == IncomeSourceKind.REIMBURSEMENT }
        projector.apply(stored(99, IncomeSourceCreated(system.id, system.name, null)))
        assertEquals(IncomeSourceKind.REIMBURSEMENT, dao.incomeSources.getValue(system.id.value).kind)
    }

    @Test
    fun eventsOfOtherProjectorsAreIgnoredAndMissingRowsDoNotCrash() = runTest {
        projector.apply(stored(1, AccountRenamed(AccountId("ghost"), "x")))
        projector.apply(stored(2, AccountArchived(AccountId("ghost"))))
        assertNull(dao.accounts["ghost"])
    }

    @Test
    fun replayProducesTheSameProjection() = runTest {
        applyAll()
        val first = dao.snapshot()

        projector.reset()
        assertTrue(dao.snapshot().all { it.isEmpty() })
        applyAll()
        assertEquals(first, dao.snapshot())

        val otherDao = FakeCatalogDao()
        applyAll(CatalogProjector(otherDao))
        assertEquals(first, otherDao.snapshot())
    }
}
