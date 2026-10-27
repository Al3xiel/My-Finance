package com.al3xiel.my_finances.feature.accounts.domain

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.AccountArchived
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.CategoryArchived
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.CategoryRenamed
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.PersonArchived
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.model.event.PersonRenamed
import com.al3xiel.my_finances.domain.model.event.TagArchived
import com.al3xiel.my_finances.domain.model.event.TagCreated
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.domain.model.event.TagRenamed
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.categories.domain.usecase.ArchiveCategoryUseCase
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.categories.domain.usecase.CreateCategoryUseCase
import com.al3xiel.my_finances.feature.categories.domain.usecase.RenameCategoryUseCase
import com.al3xiel.my_finances.feature.people.domain.usecase.ArchivePersonUseCase
import com.al3xiel.my_finances.feature.people.domain.usecase.CreatePersonUseCase
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.usecase.RenamePersonUseCase
import com.al3xiel.my_finances.feature.tags.domain.usecase.ArchiveTagUseCase
import com.al3xiel.my_finances.feature.tags.domain.usecase.CreateTagUseCase
import com.al3xiel.my_finances.feature.tags.domain.usecase.RenameTagUseCase
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.accounts.domain.usecase.ArchiveAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateIncomeSourceUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RenameAccountUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class CatalogUseCasesTest {
    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val clock = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC)
    private val recorder = EventRecorder(store, ids, clock)

    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()
    private val tags = FakeTagRepository()
    private val people = FakePersonRepository()
    private val sources = FakeIncomeSourceRepository()

    private val a1 = AccountId("a1")
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private fun account(name: String, archived: Boolean = false) =
        Account(a1, name, AccountType.BANK, CurrencyCode.PEN, null, archived)

    private fun failed(error: BusinessError) = Outcome.Failed(error)

    // ── Cuentas ─────────────────────────────────────────────────────────────────────────

    @Test
    fun createAccountEmitsOneEventDatedTodayWithGeneratedId() = runTest {
        val result = CreateAccountUseCase(accounts, recorder, ids)("BBVA", AccountType.BANK, "#112233", pen(100_000))

        assertEquals(Outcome.Ok(AccountId("id-1")), result)
        val stored = store.events.single()
        assertEquals(
            AccountCreated(AccountId("id-1"), "BBVA", AccountType.BANK, CurrencyCode.PEN, "#112233", pen(100_000)),
            stored.event,
        )
        assertEquals(LocalDate.of(2026, 10, 26), stored.occurredOn)
        assertEquals("id-2", stored.correlationId)
    }

    @Test
    fun createAccountAcceptsZeroOpeningBalance() = runTest {
        val result = CreateAccountUseCase(accounts, recorder, ids)("Efectivo", AccountType.CASH, null, pen(0))
        assertEquals(Outcome.Ok(AccountId("id-1")), result)
    }

    @Test
    fun createAccountRejectsInvalidInputWithoutEmitting() = runTest {
        accounts.accounts[a1] = account("Ahorros")
        val create = CreateAccountUseCase(accounts, recorder, ids)

        assertEquals(failed(BusinessError.BlankName), create("   ", AccountType.BANK, null, pen(0)))
        assertEquals(failed(BusinessError.DuplicateName), create(" ahórros ", AccountType.BANK, null, pen(0)))
        assertEquals(failed(BusinessError.NegativeAmount), create("Otra", AccountType.BANK, null, pen(-1)))
        assertEquals(
            failed(BusinessError.UnsupportedCurrency),
            create("Dólares", AccountType.BANK, null, Money(0, CurrencyCode.USD)),
        )
        assertEquals(0, store.events.size)
    }

    @Test
    fun archivedNameCanBeUsedAgain() = runTest {
        accounts.accounts[a1] = account("Ahorros", archived = true)
        val result = CreateAccountUseCase(accounts, recorder, ids)("Ahorros", AccountType.BANK, null, pen(0))
        assertEquals(Outcome.Ok(AccountId("id-1")), result)
    }

    @Test
    fun renameAccountChecksExistenceAndUniqueness() = runTest {
        accounts.accounts[a1] = account("BBVA")
        accounts.accounts[AccountId("a2")] = account("BCP").copy(id = AccountId("a2"))
        val rename = RenameAccountUseCase(accounts, recorder)

        assertEquals(failed(BusinessError.NotFound), rename(AccountId("ghost"), "X"))
        assertEquals(failed(BusinessError.DuplicateName), rename(a1, "bcp"))
        assertEquals(failed(BusinessError.BlankName), rename(a1, " "))
        assertEquals(Outcome.Ok(Unit), rename(a1, "bbva"))
        assertEquals(AccountRenamed(a1, "bbva"), store.events.single().event)
    }

    @Test
    fun archiveAccountNeedsZeroBalance() = runTest {
        accounts.accounts[a1] = account("BBVA")
        val archive = ArchiveAccountUseCase(accounts, recorder)

        accounts.balances[a1] = 500
        assertEquals(failed(BusinessError.BalanceNotZero), archive(a1))
        assertEquals(0, store.events.size)

        accounts.balances[a1] = 0
        assertEquals(Outcome.Ok(Unit), archive(a1))
        assertEquals(AccountArchived(a1), store.events.single().event)

        accounts.accounts[a1] = account("BBVA", archived = true)
        assertEquals(failed(BusinessError.AlreadyArchived), archive(a1))
        assertEquals(failed(BusinessError.NotFound), archive(AccountId("ghost")))
    }

    // ── Categorías ──────────────────────────────────────────────────────────────────────

    private fun category(id: String, name: String, parent: String? = null, archived: Boolean = false) =
        Category(CategoryId(id), name, parent?.let(::CategoryId), archived)

    @Test
    fun createCategoryAllowsOneLevelOnly() = runTest {
        categories.categories[CategoryId("c1")] = category("c1", "Comida")
        categories.categories[CategoryId("c2")] = category("c2", "Salidas", parent = "c1")
        categories.categories[CategoryId("c3")] = category("c3", "Vieja", archived = true)
        val create = CreateCategoryUseCase(categories, recorder, ids)

        assertEquals(Outcome.Ok(CategoryId("id-1")), create("Transporte", "bus", "#00FF00", null))
        assertEquals(CategoryCreated(CategoryId("id-1"), "Transporte", "bus", "#00FF00", null), store.events.single().event)
        assertEquals(Outcome.Ok(CategoryId("id-3")), create("Restaurantes", "dining", "#FF0000", CategoryId("c1")))
        assertEquals(failed(BusinessError.InvalidParent), create("Nieta", "x", "#000000", CategoryId("c2")))
        assertEquals(failed(BusinessError.InvalidParent), create("Huérfana", "x", "#000000", CategoryId("ghost")))
        assertEquals(failed(BusinessError.InvalidParent), create("De archivada", "x", "#000000", CategoryId("c3")))
        assertEquals(failed(BusinessError.DuplicateName), create("comida", "x", "#000000", null))
    }

    @Test
    fun renameAndArchiveCategory() = runTest {
        categories.categories[CategoryId("c1")] = category("c1", "Comida")
        val rename = RenameCategoryUseCase(categories, recorder)
        val archive = ArchiveCategoryUseCase(categories, recorder)

        assertEquals(Outcome.Ok(Unit), rename(CategoryId("c1"), "Alimentos"))
        assertEquals(Outcome.Ok(Unit), archive(CategoryId("c1")))
        assertEquals(
            listOf(CategoryRenamed(CategoryId("c1"), "Alimentos"), CategoryArchived(CategoryId("c1"))),
            store.events.map { it.event },
        )
        assertEquals(failed(BusinessError.NotFound), rename(CategoryId("ghost"), "x"))
    }

    // ── Personas ────────────────────────────────────────────────────────────────────────

    private val p1 = PersonId("p1")

    @Test
    fun personLifecycle() = runTest {
        val create = CreatePersonUseCase(people, recorder, ids)
        assertEquals(Outcome.Ok(PersonId("id-1")), create("Mamá"))
        assertEquals(PersonCreated(PersonId("id-1"), "Mamá"), store.events.single().event)

        people.people[p1] = Person(p1, "Mamá", false)
        assertEquals(failed(BusinessError.DuplicateName), create("mama"))
        assertEquals(failed(BusinessError.BlankName), create(""))

        assertEquals(Outcome.Ok(Unit), RenamePersonUseCase(people, recorder)(p1, "Mami"))
        assertEquals(Outcome.Ok(Unit), ArchivePersonUseCase(people, recorder)(p1))
        assertEquals(listOf(PersonRenamed(p1, "Mami"), PersonArchived(p1)), store.events.takeLast(2).map { it.event })
    }

    // ── Conceptos ───────────────────────────────────────────────────────────────────────

    @Test
    fun personTagNeedsAnActivePersonAndTopicTagMustNotHaveOne() = runTest {
        people.people[p1] = Person(p1, "Mamá", false)
        people.people[PersonId("p2")] = Person(PersonId("p2"), "Papá", true)
        val create = CreateTagUseCase(tags, people, recorder, ids)

        assertEquals(failed(BusinessError.PersonRequired), create("Mamá", TagKind.PERSON, null))
        assertEquals(failed(BusinessError.PersonRequired), create("Mamá", TagKind.PERSON, PersonId("ghost")))
        assertEquals(failed(BusinessError.PersonRequired), create("Papá", TagKind.PERSON, PersonId("p2")))
        assertEquals(failed(BusinessError.PersonNotAllowed), create("Viaje", TagKind.TOPIC, p1))
        assertEquals(0, store.events.size)

        assertEquals(Outcome.Ok(TagId("id-1")), create("Mamá", TagKind.PERSON, p1))
        assertEquals(Outcome.Ok(TagId("id-3")), create("Viaje", TagKind.TOPIC, null))
        assertEquals(
            listOf(
                TagCreated(TagId("id-1"), "Mamá", TagKind.PERSON, p1),
                TagCreated(TagId("id-3"), "Viaje", TagKind.TOPIC, null),
            ),
            store.events.map { it.event },
        )
    }

    @Test
    fun tagNamesAreUniqueAndCanBeRenamedAndArchived() = runTest {
        val t1 = TagId("t1")
        tags.tags[t1] = Tag(t1, "Viaje", TagKind.TOPIC, null, false)
        val create = CreateTagUseCase(tags, people, recorder, ids)

        assertEquals(failed(BusinessError.DuplicateName), create("VIAJE", TagKind.TOPIC, null))
        assertEquals(Outcome.Ok(Unit), RenameTagUseCase(tags, recorder)(t1, "Viajes"))
        assertEquals(Outcome.Ok(Unit), ArchiveTagUseCase(tags, recorder)(t1))
        assertEquals(listOf(TagRenamed(t1, "Viajes"), TagArchived(t1)), store.events.map { it.event })
    }

    // ── Fuentes de ingreso ──────────────────────────────────────────────────────────────

    @Test
    fun createIncomeSource() = runTest {
        people.people[p1] = Person(p1, "Mamá", false)
        sources.names += "Sueldo"
        val create = CreateIncomeSourceUseCase(sources, people, recorder, ids)

        assertEquals(failed(BusinessError.DuplicateName), create("sueldo", null))
        assertEquals(failed(BusinessError.NotFound), create("Regalo", PersonId("ghost")))
        assertEquals(Outcome.Ok(IncomeSourceId("id-1")), create("Mamá", p1))
        assertEquals(IncomeSourceCreated(IncomeSourceId("id-1"), "Mamá", p1), store.events.single().event)
    }
}
