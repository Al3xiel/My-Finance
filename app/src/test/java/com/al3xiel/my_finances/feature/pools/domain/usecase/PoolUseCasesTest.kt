package com.al3xiel.my_finances.feature.pools.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolArchived
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolCreated
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolRenamed
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakeCategoryRepository
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.accounts.domain.FakeTagRepository
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PoolUseCasesTest {
    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val clock = Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC)
    private val recorder = EventRecorder(store, ids, clock)

    private val pools = FakePoolRepository()
    private val people = FakePersonRepository()
    private val categories = FakeCategoryRepository()
    private val tags = FakeTagRepository()

    private val create = CreatePoolUseCase(pools, people, categories, tags, recorder, ids)
    private val rename = RenamePoolUseCase(pools, recorder)
    private val archive = ArchivePoolUseCase(pools, recorder)

    private val mom = PersonId("mom")
    private val pets = CategoryId("pets")
    private val dogTag = TagId("dog")
    private val dogs = PoolId("dogs")

    init {
        people.people[mom] = Person(mom, "Mamá", false)
        categories.categories[pets] = Category(pets, "Mascotas", null, false)
        tags.tags[dogTag] = Tag(dogTag, "Perros", TagKind.TOPIC, null, false)
    }

    private fun failed(error: BusinessError) = Outcome.Failed(error)

    private fun existing(name: String = "Perros – mamá", archived: Boolean = false) {
        pools.pools[dogs] = Pool(dogs, name, mom, PoolKind.PERMANENT, null, emptyList(), archived)
    }

    @Test
    fun createsAPoolWithTheResponsibleAndTheDefaults() = runTest {
        val result = create("  Perros – mamá ", mom, PoolKind.PERMANENT, pets, listOf(dogTag, dogTag))

        assertEquals(Outcome.Ok(PoolId("id-1")), result)
        assertEquals(
            ReimbursementPoolCreated(PoolId("id-1"), "Perros – mamá", mom, PoolKind.PERMANENT, pets, listOf(dogTag)),
            store.events.single().event,
        )
    }

    @Test
    fun defaultsAreOptional() = runTest {
        create("Viaje", mom, PoolKind.EVENT, null, emptyList())

        assertEquals(
            ReimbursementPoolCreated(PoolId("id-1"), "Viaje", mom, PoolKind.EVENT, null, emptyList()),
            store.events.single().event,
        )
    }

    @Test
    fun theResponsibleIsRequiredAndMustBeAnActivePerson() = runTest {
        people.people[PersonId("old")] = Person(PersonId("old"), "Tío", true)

        assertEquals(failed(BusinessError.PersonRequired), create("Perros", null, PoolKind.PERMANENT, null, emptyList()))
        assertEquals(failed(BusinessError.NotFound), create("Perros", PersonId("ghost"), PoolKind.PERMANENT, null, emptyList()))
        assertEquals(failed(BusinessError.AlreadyArchived), create("Perros", PersonId("old"), PoolKind.PERMANENT, null, emptyList()))
        assertEquals(0, store.events.size)
    }

    @Test
    fun rejectsBlankDuplicateAndUnknownOrArchivedDefaults() = runTest {
        existing()
        categories.categories[CategoryId("old")] = Category(CategoryId("old"), "Viejo", null, true)
        tags.tags[TagId("oldtag")] = Tag(TagId("oldtag"), "Viejo", TagKind.TOPIC, null, true)

        assertEquals(failed(BusinessError.BlankName), create("  ", mom, PoolKind.PERMANENT, null, emptyList()))
        assertEquals(failed(BusinessError.DuplicateName), create("perros – MAMA", mom, PoolKind.PERMANENT, null, emptyList()))
        assertEquals(failed(BusinessError.NotFound), create("Otro", mom, PoolKind.PERMANENT, CategoryId("ghost"), emptyList()))
        assertEquals(failed(BusinessError.AlreadyArchived), create("Otro", mom, PoolKind.PERMANENT, CategoryId("old"), emptyList()))
        assertEquals(failed(BusinessError.NotFound), create("Otro", mom, PoolKind.PERMANENT, null, listOf(TagId("ghost"))))
        assertEquals(failed(BusinessError.AlreadyArchived), create("Otro", mom, PoolKind.PERMANENT, null, listOf(TagId("oldtag"))))
        assertEquals(0, store.events.size)
    }

    @Test
    fun renamesKeepingItsOwnNameAllowedAndRejectingOthers() = runTest {
        existing()
        pools.pools[PoolId("cats")] = Pool(PoolId("cats"), "Gatos", mom, PoolKind.PERMANENT, null, emptyList(), false)

        assertEquals(Outcome.Ok(Unit), rename(dogs, " Perritos "))
        assertEquals(ReimbursementPoolRenamed(dogs, "Perritos"), store.events.single().event)
        assertEquals(Outcome.Ok(Unit), rename(dogs, "perros – mamá"))
        assertEquals(failed(BusinessError.DuplicateName), rename(dogs, "gatos"))
        assertEquals(failed(BusinessError.BlankName), rename(dogs, ""))
        assertEquals(failed(BusinessError.NotFound), rename(PoolId("ghost"), "x"))
    }

    @Test
    fun archivedPoolsCannotBeRenamed() = runTest {
        existing(archived = true)

        assertEquals(failed(BusinessError.AlreadyArchived), rename(dogs, "Otro"))
    }

    @Test
    fun archivingIsRejectedWhileSomethingIsPending() = runTest {
        existing()
        pools.pending[dogs] = 1

        assertEquals(failed(BusinessError.PendingNotZero), archive(dogs))
        assertEquals(0, store.events.size)

        pools.pending[dogs] = 0
        assertEquals(Outcome.Ok(Unit), archive(dogs))
        assertEquals(ReimbursementPoolArchived(dogs), store.events.single().event)
    }

    @Test
    fun archivingRejectsUnknownAndAlreadyArchived() = runTest {
        existing(archived = true)

        assertEquals(failed(BusinessError.AlreadyArchived), archive(dogs))
        assertEquals(failed(BusinessError.NotFound), archive(PoolId("ghost")))
    }
}
