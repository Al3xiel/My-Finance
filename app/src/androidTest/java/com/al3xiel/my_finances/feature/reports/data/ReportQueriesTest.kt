package com.al3xiel.my_finances.feature.reports.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Las consultas de reportes (D35) sobre filas escritas a mano: no dependen de los proyectores. */
@RunWith(AndroidJUnit4::class)
class ReportQueriesTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ReportRepositoryImpl
    private val clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC)
    private val october = ReportPeriod.Month(YearMonth.of(2026, 10))
    private val november = ReportPeriod.Month(YearMonth.of(2026, 11))

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = ReportRepositoryImpl(database.reportQueryDao(), clock)
        sql("INSERT INTO categories VALUES ('food','Food','i','c',NULL,0)")
        sql("INSERT INTO categories VALUES ('market','Market','i','c','food',0)")
    }

    @After fun tearDown() = database.close()

    private fun sql(statement: String) = database.openHelper.writableDatabase.execSQL(statement)

    private fun movement(id: String, type: String, on: String, status: String = "ACTIVE", amount: Long = 100) =
        sql("INSERT INTO movements VALUES ('$id','$type','acc',NULL,$amount,'PEN',NULL,NULL,NULL,'$on','$status',1,1)")

    private fun part(id: String, movementId: String, amount: Long, kind: String = "NORMAL", pool: String? = null, category: String = "food") =
        sql(
            "INSERT INTO movement_parts (id, movement_id, amount_minor, category_id, kind, pool_id, reimbursed_minor, reimbursement_status, created_sequence) " +
                "VALUES ('$id','$movementId',$amount,'$category','$kind',${pool?.let { "'$it'" } ?: "NULL"},0,'PENDING',1)",
        )

    private fun reimbursement(id: String, movementId: String, partId: String, amount: Long, on: String) {
        sql("INSERT INTO reimbursements VALUES ('$id','p','$movementId',$amount,'OLDEST_FIRST','$on')")
        sql("INSERT INTO reimbursement_allocations VALUES ('$id','$partId',$amount)")
    }

    @Test fun periodBoundsAndStatusFilterTheParts() = runBlocking {
        movement("in", "EXPENSE", "2026-10-01"); part("a", "in", 10)
        movement("end", "EXPENSE", "2026-10-31"); part("b", "end", 20)
        movement("out", "EXPENSE", "2026-11-01"); part("c", "out", 30)
        movement("void", "EXPENSE", "2026-10-15", status = "VOIDED"); part("d", "void", 40)
        assertEquals(listOf("a", "b"), repository.parts(october).map { it.partId })
    }

    @Test fun feeAndUntrackedPartsCountAndSubcategoryKeepsItsParent() = runBlocking {
        movement("t", "TRANSFER", "2026-10-05"); part("t:fee", "t", 2, kind = "FEE")
        movement("u", "FUND_UNTRACKED_SPEND", "2026-10-06"); part("u-untracked", "u", 5, kind = "UNTRACKED", category = "market")
        val rows = repository.parts(october)
        assertEquals(listOf(2L, 5L), rows.map { it.amount })
        assertEquals("food", rows.last().categoryParentId)
    }

    @Test fun reimbursementCountsOnlyUpToTheClosingDate() = runBlocking {
        movement("e", "EXPENSE", "2026-10-10", amount = 120); part("p", "e", 120, pool = "pool")
        movement("r", "REIMBURSEMENT", "2026-11-05", amount = 120)
        reimbursement("rb", "r", "p", 120, "2026-11-05")
        assertEquals(0L, repository.parts(october).single().reimbursed) // octubre cierra el 31: el reembolso es posterior
        assertEquals(120L, repository.parts(ReportPeriod.Custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 20))).single().reimbursed)
        assertEquals(0L, repository.summary(november).ownSpend) // el gasto de octubre no pasa a noviembre
    }

    @Test fun voidedReimbursementDoesNotCount() = runBlocking {
        movement("e", "EXPENSE", "2026-10-10", amount = 120); part("p", "e", 120, pool = "pool")
        movement("r", "REIMBURSEMENT", "2026-10-20", status = "VOIDED", amount = 120)
        reimbursement("rb", "r", "p", 120, "2026-10-20")
        assertEquals(0L, repository.parts(october).single().reimbursed)
        assertEquals(120L, repository.summary(october).ownSpend)
    }

    @Test fun ledgerSkipsVoidedEntriesAndTagsAreGroupedByPart() = runBlocking {
        sql("INSERT INTO ledger_entries VALUES ('l1',1,NULL,'acc',500,'INCOME','2026-10-02',0,NULL)")
        sql("INSERT INTO ledger_entries VALUES ('l2',2,NULL,'acc',700,'INCOME','2026-10-03',1,9)")
        sql("INSERT INTO ledger_entries VALUES ('l3',3,NULL,'acc',-30,'ADJUSTMENT','2026-10-04',0,NULL)")
        movement("e", "EXPENSE", "2026-10-10"); part("p", "e", 50)
        sql("INSERT INTO movement_part_tags VALUES ('p','t1')")
        sql("INSERT INTO movement_part_tags VALUES ('p','t2')")
        val summary = repository.summary(october)
        assertEquals(500L, summary.income)
        assertEquals(-30L, summary.unidentified)
        assertEquals(listOf("t1", "t2"), repository.parts(october).single().tagIds)
    }

    @Test fun detailsCarryDateKindAndNoteOfActiveParts() = runBlocking {
        sql("INSERT INTO movements VALUES ('n','EXPENSE','acc',NULL,10,'PEN',NULL,NULL,'Menú','2026-10-07','ACTIVE',1,1)")
        part("np", "n", 10)
        movement("v", "EXPENSE", "2026-10-08", status = "VOIDED"); part("vp", "v", 10)
        val detail = repository.partDetails(october).getValue("np")
        assertEquals(LocalDate.of(2026, 10, 7), detail.date)
        assertEquals("Menú", detail.note)
        assertEquals(setOf("np"), repository.partDetails(october).keys)
    }
}
