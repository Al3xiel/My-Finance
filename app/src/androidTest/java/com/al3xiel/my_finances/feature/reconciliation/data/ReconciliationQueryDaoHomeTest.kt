package com.al3xiel.my_finances.feature.reconciliation.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.projection.reconciliation.ReconciliationEntity
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReconciliationQueryDaoHomeTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ReconciliationRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = ReconciliationRepositoryImpl(database.reconciliationQueryDao())
    }

    @After
    fun tearDown() = database.close()

    private suspend fun put(id: String, sequence: Long, status: ReconciliationStatus, needsReview: Boolean = false) {
        database.reconciliationDao().upsert(
            ReconciliationEntity(
                id = id, accountId = "acc-$id", currency = CurrencyCode.PEN, realMinor = 0, snapshotComputedMinor = 0,
                checkedSequence = sequence, status = status, cause = null, adjustmentMovementId = null, adjustmentMinor = null,
                cancelReason = null, checkedAt = "2026-10-25T00:00:00Z", resolvedAt = null, needsReview = needsReview,
            ),
        )
    }

    @Test
    fun withoutReconciliationsThereIsNothing() = runBlocking {
        assertNull(repository.observeOldestNeedsReviewId().first())
        assertNull(repository.observeOpenId().first())
    }

    @Test
    fun findsTheOldestOneThatNeedsReview() = runBlocking {
        put("new", 30, ReconciliationStatus.RESOLVED, needsReview = true)
        put("old", 10, ReconciliationStatus.RESOLVED, needsReview = true)
        put("fine", 5, ReconciliationStatus.RESOLVED)

        assertEquals(ReconciliationId("old"), repository.observeOldestNeedsReviewId().first())
    }

    @Test
    fun findsTheOldestOpenOneAndIgnoresTheOthers() = runBlocking {
        put("done", 1, ReconciliationStatus.RESOLVED)
        put("b", 20, ReconciliationStatus.OPEN)
        put("a", 10, ReconciliationStatus.OPEN)

        assertEquals(ReconciliationId("a"), repository.observeOpenId().first())
    }

    @Test
    fun theIdsDisappearWhenTheCauseIsResolved() = runBlocking {
        put("r", 10, ReconciliationStatus.OPEN)
        assertEquals(ReconciliationId("r"), repository.observeOpenId().first())

        put("r", 10, ReconciliationStatus.RESOLVED)
        assertNull(repository.observeOpenId().first())
    }
}
