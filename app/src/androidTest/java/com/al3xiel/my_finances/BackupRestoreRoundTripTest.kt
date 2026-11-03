package com.al3xiel.my_finances

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.id.UuidIdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.database.DATABASE_VERSION
import com.al3xiel.my_finances.data.eventstore.EventEntity
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.data.projection.ProjectionRebuilderImpl
import com.al3xiel.my_finances.data.projection.catalog.CatalogProjector
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.ChainRow
import com.al3xiel.my_finances.domain.usecase.ChainVerification
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.ReplayProjectionsUseCase
import com.al3xiel.my_finances.domain.usecase.RestoreMarker
import com.al3xiel.my_finances.domain.usecase.VerifyEventChainUseCase
import com.al3xiel.my_finances.feature.accounts.data.AccountRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateAccountUseCase
import com.al3xiel.my_finances.feature.backup.domain.format.BackupImporter
import com.al3xiel.my_finances.feature.backup.data.DatabaseFiles
import com.al3xiel.my_finances.feature.backup.domain.format.StagingSink
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.backup.domain.usecase.CanRestoreUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.CreateBackupResult
import com.al3xiel.my_finances.feature.backup.domain.usecase.CreateBackupUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.RestoreBackupUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.RestoreResult
import com.al3xiel.my_finances.feature.categories.data.CategoryRepositoryImpl
import com.al3xiel.my_finances.feature.categories.domain.usecase.CreateCategoryUseCase
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.people.data.PersonRepositoryImpl
import com.al3xiel.my_finances.feature.privacy.data.CacheWipeStep
import com.al3xiel.my_finances.feature.privacy.data.DatabasesWipeStep
import com.al3xiel.my_finances.feature.tags.data.TagRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.data.IncomeSourceRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

private class MemoryFiles : BackupFilePort {
    val files = mutableMapOf<String, ByteArray>()

    override suspend fun openOutput(uri: String): OutputStream {
        val buffer = ByteArrayOutputStream()
        return object : OutputStream() {
            override fun write(b: Int) = buffer.write(b)
            override fun write(source: ByteArray, offset: Int, length: Int) = buffer.write(source, offset, length)
            override fun close() {
                files[uri] = buffer.toByteArray()
            }
        }
    }

    override suspend fun openInput(uri: String): InputStream? = files[uri]?.let { ByteArrayInputStream(it) }
    override suspend fun sizeOf(uri: String): Long? = files[uri]?.size?.toLong()
    override suspend fun delete(uri: String) = files.remove(uri) != null
}

/** Un staging que escribe en una segunda base Room real (las mismas tablas y triggers que la de producción). */
private class DatabaseSink(private val database: AppDatabase, private val log: MutableList<String>) : StagingSink {
    override suspend fun insert(rows: List<ChainRow>) {
        database.withTransaction { database.eventDao().insertRaw(rows.map { it.toEntity() }) }
    }

    override suspend fun commit() {
        log += "commit"
    }

    override suspend fun discard() {
        log += "discard"
    }

    private fun ChainRow.toEntity() = EventEntity(
        sequence = sequence,
        eventId = fields.eventId,
        type = fields.type,
        schemaVersion = fields.schemaVersion,
        aggregateType = fields.aggregateType,
        aggregateId = fields.aggregateId,
        correlationId = fields.correlationId,
        payload = fields.payload,
        occurredOn = fields.occurredOn,
        recordedAt = fields.recordedAt,
        appVersion = fields.appVersion,
        previousHash = fields.previousHash,
        hash = hash,
    )
}

private class Marker : RestoreMarker {
    var pending = false
    override suspend fun isPending() = pending
    override suspend fun mark() { pending = true }
    override suspend fun clear() { pending = false }
}

/**
 * Respaldar, borrar y restaurar con bases Room reales: casos de uso reales, registro real, importador real. Lo que no
 * cubre es el intercambio del archivo (`DatabaseFiles`, probado con archivos reales abajo) ni el reinicio del proceso.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreRoundTripTest {
    private class World(val database: AppDatabase) {
        val projectors = setOf(CatalogProjector(database.catalogDao()), MovementProjector(database.movementDao(), database.ledgerDao()))
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day = LocalDate.of(2026, 10, 26)
    private val zone = ZoneId.of("America/Lima")
    private val clock = Clock.fixed(day.atTime(15, 0).atZone(zone).toInstant(), zone)
    private val ids = UuidIdGenerator()
    private val password = "correct horse battery".toCharArray()

    private lateinit var source: World
    private lateinit var target: World
    private lateinit var sourceStore: EventStoreImpl
    private lateinit var targetStore: EventStoreImpl
    private val files = MemoryFiles()
    private val sinkLog = mutableListOf<String>()
    private val marker = Marker()
    private var swaps = 0
    private var restarts = 0

    private fun newDatabase() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).addCallback(EventTriggersCallback).build()

    @Before
    fun setUp() {
        source = World(newDatabase())
        target = World(newDatabase())
        sourceStore = EventStoreImpl(source.database, source.projectors, ids, clock, "1.0")
        targetStore = EventStoreImpl(target.database, target.projectors, ids, clock, "1.0")
    }

    @After
    fun tearDown() {
        source.database.close()
        target.database.close()
    }

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value

    private suspend fun seedSource() {
        val db = source.database
        val accounts = AccountRepositoryImpl(db.catalogDao(), db.catalogQueryDao(), db.ledgerQueryDao())
        val categories = CategoryRepositoryImpl(db.catalogDao(), db.catalogQueryDao())
        val tags = TagRepositoryImpl(db.catalogDao(), db.catalogQueryDao())
        val sources = IncomeSourceRepositoryImpl(db.catalogDao(), db.catalogQueryDao())
        val recorder = EventRecorder(sourceStore, ids, clock)
        val rules = MovementRules(accounts, categories, tags, sources, clock)
        val bbva = CreateAccountUseCase(accounts, recorder, ids)("BBVA", AccountType.BANK, null, pen(100_000)).value()
        val food = CreateCategoryUseCase(categories, recorder, ids)("Comida", "food", "#8D6E63", null).value()
        RegisterExpenseUseCase(rules, accounts, recorder, ids, MovementWriteLock())(
            bbva, pen(12_000), listOf(ExpensePartInput(null, pen(12_000), food, emptyList())), "Almuerzo", day,
        ).value()
    }

    private fun createBackup() = CreateBackupUseCase(
        sourceStore, files, EventRecorder(sourceStore, ids, clock), clock, ids, "1.0", DATABASE_VERSION, iterations = 1_000,
    )

    private fun restoreInto(store: EventStoreImpl, database: AppDatabase) = RestoreBackupUseCase(
        files,
        CanRestoreUseCase(store),
        BackupImporter({ DatabaseSink(database, sinkLog) }, ids, clock, "1.0"),
        { swaps++ },
        marker,
        { restarts++ },
    )

    private fun dump(database: AppDatabase, table: String, order: String): List<List<String?>> =
        database.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY $order").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) }) }
        }

    private fun projections(database: AppDatabase) = mapOf(
        "accounts" to dump(database, "accounts", "id"),
        "categories" to dump(database, "categories", "id"),
        "movements" to dump(database, "movements", "id"),
        "movement_parts" to dump(database, "movement_parts", "id"),
        "ledger_entries" to dump(database, "ledger_entries", "id"),
        "account_balances" to dump(database, "account_balances", "account_id"),
    )

    private suspend fun replayTarget() {
        val replay = ReplayProjectionsUseCase(
            targetStore, ProjectionRebuilderImpl(target.database, target.projectors, targetStore, clock), ids, clock,
        )
        replay("restore")
    }

    private suspend fun backedUp(): ByteArray {
        seedSource()
        val result = createBackup()("doc://backup", password)
        assertTrue(result is CreateBackupResult.Created)
        return files.files.getValue("doc://backup")
    }

    @Test
    fun backingUpThenRestoringReproducesTheProjectionsAndKeepsTheChainIntact() = runBlocking {
        backedUp()
        val before = projections(source.database)

        val result = restoreInto(targetStore, target.database)("doc://backup", password.copyOf())

        assertTrue(result is RestoreResult.Restored)
        assertEquals(listOf("commit"), sinkLog)
        assertEquals(1, swaps)
        assertEquals(1, restarts)
        assertTrue(marker.pending)
        replayTarget()
        assertEquals(before, projections(target.database))
        val count = targetStore.readAll().size
        assertEquals(ChainVerification.Valid(count), VerifyEventChainUseCase(targetStore)())
        assertTrue(targetStore.readAll().any { it.event.type == "BackupRestored" })
    }

    @Test
    fun aWrongPasswordRestoresNothing() = runBlocking {
        backedUp()

        val result = restoreInto(targetStore, target.database)("doc://backup", "not the password".toCharArray())

        assertTrue(result is RestoreResult.Rejected)
        assertEquals(0, swaps)
        assertTrue(targetStore.readAll().isEmpty())
    }

    @Test
    fun aFileAlteredInAnyRegionIsNeverRestored() = runBlocking {
        val original = backedUp()
        val positions = listOf(0, 5, 20, 40, original.size / 4, original.size / 2, original.size - 20, original.size - 1)
        positions.forEach { position ->
            files.files["doc://tampered"] = original.copyOf().also { it[position] = (it[position].toInt() xor 0x01).toByte() }
            val result = restoreInto(targetStore, target.database)("doc://tampered", password.copyOf())
            assertFalse("position $position", result is RestoreResult.Restored)
        }
        files.files["doc://truncated"] = original.copyOf(original.size - 10)
        assertFalse(restoreInto(targetStore, target.database)("doc://truncated", password.copyOf()) is RestoreResult.Restored)
        assertEquals(0, swaps)
        assertEquals(0, restarts)
        assertFalse(marker.pending)
    }

    @Test
    fun aDatabaseWithUserDataRefusesToRestoreWithoutTouchingTheFile() = runBlocking {
        backedUp()
        val accounts = AccountRepositoryImpl(target.database.catalogDao(), target.database.catalogQueryDao(), target.database.ledgerQueryDao())
        CreateAccountUseCase(accounts, EventRecorder(targetStore, ids, clock), ids)("Mine", AccountType.CASH, null, pen(0)).value()

        val result = restoreInto(targetStore, target.database)("doc://backup", password.copyOf())

        assertEquals(RestoreResult.NotAllowed, result)
        assertTrue(sinkLog.isEmpty())
        assertEquals(0, swaps)
    }

    @Test
    fun theTotalWipeRemovesTheStagingTheLeftoversAndTheCache() = runBlocking {
        listOf(DatabaseFiles.STAGING, "${DatabaseFiles.LIVE}.old").forEach { context.openOrCreateDatabase(it, Context.MODE_PRIVATE, null).close() }
        File(context.cacheDir, "backup-temp.tmp").writeText("x")
        assertTrue(context.databaseList().contains(DatabaseFiles.STAGING))

        DatabasesWipeStep(context).wipe()
        CacheWipeStep(context).wipe()

        assertTrue(context.databaseList().isEmpty())
        assertTrue(context.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun theFileSwapReplacesTheLiveDatabaseAndCleansUp() {
        val directory = File(context.cacheDir, "swap-test").apply { deleteRecursively(); mkdirs() }
        File(directory, DatabaseFiles.LIVE).writeText("old")
        File(directory, DatabaseFiles.STAGING).writeText("new")
        File(directory, DatabaseFiles.STAGING + "-wal").writeText("w")

        DatabaseFiles(directory).swap()

        assertEquals("new", File(directory, DatabaseFiles.LIVE).readText())
        assertEquals(listOf(DatabaseFiles.LIVE), directory.list().orEmpty().toList())
        directory.deleteRecursively()
    }
}
