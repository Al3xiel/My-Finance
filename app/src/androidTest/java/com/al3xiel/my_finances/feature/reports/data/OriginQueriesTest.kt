package com.al3xiel.my_finances.feature.reports.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.rules.OriginCalculator
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Las consultas de origen (S44) sobre filas escritas a mano: no dependen del proyector. */
@RunWith(AndroidJUnit4::class)
class OriginQueriesTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: OriginRepositoryImpl
    private val october = ReportPeriod.Month(YearMonth.of(2026, 10))

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = OriginRepositoryImpl(database.originQueryDao())
        sql("INSERT INTO categories VALUES ('food','Food','i','c',NULL,0)")
        sql("INSERT INTO categories VALUES ('milk','Milk','i','c','food',0)")
    }

    @After fun tearDown() = database.close()

    private fun sql(statement: String) = database.openHelper.writableDatabase.execSQL(statement)

    private fun movement(id: String, on: String, status: String = "ACTIVE") =
        sql("INSERT INTO movements VALUES ('$id','EXPENSE','acc',NULL,100,'PEN',NULL,NULL,NULL,'$on','$status',1,1)")

    private fun part(id: String, movementId: String, category: String = "milk") = sql(
        "INSERT INTO movement_parts (id, movement_id, amount_minor, category_id, kind, pool_id, reimbursed_minor, reimbursement_status, created_sequence) " +
            "VALUES ('$id','$movementId',100,'$category','NORMAL',NULL,0,'PENDING',1)",
    )

    private fun entry(id: String, kind: LedgerEntryKind, on: String, voided: Int = 0, account: String = "acc") =
        sql("INSERT INTO ledger_entries VALUES ('$id',${id.removePrefix("l").toInt()},NULL,'$account',-100,'$kind','$on',$voided,NULL)")

    private fun attribution(entry: String, part: String, source: String, amount: Long) =
        sql("INSERT INTO origin_attributions VALUES ('$entry','$part','$source',$amount)")

    @Test fun compositionReadsTheBalancesPerAccount() = runBlocking {
        sql("INSERT INTO origin_balances VALUES ('acc','s1',700)")
        sql("INSERT INTO origin_balances VALUES ('acc','s2',300)")
        val account = repository.composition().single()
        assertEquals(listOf(7000, 3000), account.slices.map { it.basisPoints })
        assertEquals(1000L, account.balanceMinor)
    }

    @Test fun attributionsCarryAccountKindAndCategoryOfTheirPart() = runBlocking {
        movement("m", "2026-10-10"); part("p", "m")
        entry("l1", LedgerEntryKind.EXPENSE, "2026-10-10"); attribution("l1", "p", "s1", 60)
        val record = repository.attributions(october).single()
        assertEquals("acc", record.accountId)
        assertEquals("milk", record.categoryId)
        assertEquals("food", record.categoryParentId)
        assertEquals(60L, record.amountMinor)
    }

    @Test fun periodBoundsAndVoidedEntriesFilterTheAttributions() = runBlocking {
        movement("a", "2026-10-31"); part("pa", "a")
        movement("b", "2026-11-01"); part("pb", "b")
        movement("c", "2026-10-15"); part("pc", "c")
        entry("l1", LedgerEntryKind.EXPENSE, "2026-10-31"); attribution("l1", "pa", "s1", 10)
        entry("l2", LedgerEntryKind.EXPENSE, "2026-11-01"); attribution("l2", "pb", "s1", 20)
        entry("l3", LedgerEntryKind.EXPENSE, "2026-10-15", voided = 1); attribution("l3", "pc", "s1", 30)
        assertEquals(listOf(10L), repository.attributions(october).map { it.amountMinor })
    }

    @Test fun entriesWithoutPartKeepTheirKindForTheLoansAndUnidentifiedNodes() = runBlocking {
        entry("l1", LedgerEntryKind.LOAN_OUT, "2026-10-05"); attribution("l1", "", "s1", 40)
        val record = repository.attributions(october).single()
        assertEquals(LedgerEntryKind.LOAN_OUT, record.kind)
        assertEquals(null, record.categoryId)
    }

    @Test fun aMultiSourceExpenseFeedsTheCalculatorWithEverySource() = runBlocking {
        movement("m", "2026-10-10"); part("p", "m")
        entry("l1", LedgerEntryKind.EXPENSE, "2026-10-10"); attribution("l1", "p", "s1", 60); attribution("l1", "p", "s2", 40)
        val records = repository.attributions(october)
        assertEquals(listOf(60L), OriginCalculator.spendOf("s1", records).map { it.amountMinor })
        assertEquals(100L, OriginCalculator.sankey(records).filter { it.to.id == "food" }.sumOf { it.amountMinor })
    }
}
