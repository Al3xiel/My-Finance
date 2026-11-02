package com.al3xiel.my_finances.feature.reconciliation.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.data.projection.reconciliation.ReconciliationProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementLinkedToReconciliation
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ReconciliationQueriesTest {
    private lateinit var database: AppDatabase
    private lateinit var store: EventStoreImpl
    private lateinit var repository: ReconciliationRepositoryImpl

    private val bbva = AccountId("bbva")
    private val r1 = ReconciliationId("r1")
    private val day = LocalDate.of(2026, 10, 25)

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private suspend fun append(event: DomainEvent) {
        store.append("c", listOf(NewEvent(event, day)))
    }

    private fun expense(id: String, minor: Long) = ExpenseRegistered(
        MovementId(id), bbva, pen(minor), listOf(ExpensePartData(PartId("p-$id"), pen(minor), CategoryId("food"), emptyList(), null, null)), null, null,
    )

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        val movementDao = database.movementDao()
        val ledgerDao = database.ledgerDao()
        val projectors = setOf(
            MovementProjector(movementDao, ledgerDao),
            ReconciliationProjector(database.reconciliationDao(), LedgerPoster(movementDao, ledgerDao)),
        )
        val ids = object : IdGenerator {
            private var n = 0
            override fun newId() = "e${n++}"
        }
        store = EventStoreImpl(database, projectors, ids, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC), "1.0")
        repository = ReconciliationRepositoryImpl(database.reconciliationQueryDao())

        append(AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(80_000)))
        append(expense("m0", 500)) // antes del chequeo
        append(BalanceCheckRecorded(r1, bbva, pen(81_240), pen(79_500)))
        append(IncomeRegistered(MovementId("m1"), bbva, pen(1_000), IncomeSourceId("s1"), r1, "reembolso olvidado"))
        append(expense("m2", 300)) // posterior y sin vincular
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun theLiveDifferenceCountsTheCutAccountEntriesPlusTheLinkedOnes() = runBlocking {
        assertEquals(pen(740), repository.remainingDifference(r1))
        val detail = repository.observeDetail(r1).first()!!
        assertEquals(pen(80_500), detail.computed)
        assertEquals(pen(740), detail.difference)
        assertEquals(listOf("m1"), detail.linked.map { it.id.value })
    }

    @Test
    fun linkingASuggestionUpdatesTheDifferenceAndMovesItOutOfTheSuggestions() = runBlocking {
        assertEquals(listOf("m2"), repository.observeDetail(r1).first()!!.suggestions.map { it.id.value })

        append(MovementLinkedToReconciliation(r1, MovementId("m2")))

        val detail = repository.observeDetail(r1).first()!!
        assertEquals(pen(1_040), detail.difference)
        assertEquals(listOf("m1", "m2"), detail.linked.map { it.id.value })
        assertEquals(emptyList<String>(), detail.suggestions.map { it.id.value })
    }

    @Test
    fun aLaterVoidThatIsNotLinkedDoesNotMoveTheDifference() = runBlocking {
        append(MovementVoided(MovementId("m0"), "duplicado", null))

        assertEquals(pen(740), repository.remainingDifference(r1))
        assertEquals(listOf("m2"), repository.observeDetail(r1).first()!!.suggestions.map { it.id.value })
    }

    @Test
    fun theOpenReconciliationAndTheLatestOfTheAccountAreFound() = runBlocking {
        assertEquals(r1, repository.openFor(bbva)!!.id)
        assertEquals(ReconciliationStatus.OPEN, repository.observeLatest(bbva).first()!!.status)
        assertNull(repository.openFor(AccountId("other")))
        assertNull(repository.find(ReconciliationId("nope")))
        assertNull(repository.remainingDifference(ReconciliationId("nope")))
        assertNull(repository.observeDetail(ReconciliationId("nope")).first())
    }

    @Test
    fun countsTheOnesThatNeedReview() = runBlocking {
        assertEquals(0, repository.observeNeedsReviewCount().first())

        val row = database.reconciliationDao().reconciliation("r1")
        assertNotNull(row)
        database.reconciliationDao().upsert(row!!.copy(needsReview = true))

        assertEquals(1, repository.observeNeedsReviewCount().first())
    }
}
