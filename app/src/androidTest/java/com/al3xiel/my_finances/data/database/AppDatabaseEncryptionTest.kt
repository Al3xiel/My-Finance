package com.al3xiel.my_finances.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.data.projection.catalog.AccountEntity
import com.al3xiel.my_finances.domain.model.event.AccountType
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseEncryptionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "encryption-test.db"
    private val key = ByteArray(32) { it.toByte() }

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .openHelperFactory(SupportOpenHelperFactory(key.copyOf()))
        .build()

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun fileDoesNotOpenAsPlainSqlite() = runBlocking {
        val database = open()
        database.catalogDao().upsertAccount(
            AccountEntity("a1", "Cuenta secreta", AccountType.BANK, CurrencyCode.PEN, null, false, "2026-10-25"),
        )
        database.close()

        val plain = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            assertThrows(SQLiteException::class.java) { plain.rawQuery("SELECT * FROM sqlite_master", null).close() }
        } finally {
            plain.close()
        }
        assertEquals(false, context.getDatabasePath(name).readBytes().decodeToString().contains("Cuenta secreta"))
    }

    @Test
    fun sameKeyReadsWhatWasWritten() = runBlocking {
        open().apply {
            catalogDao().upsertAccount(AccountEntity("a1", "BBVA", AccountType.BANK, CurrencyCode.PEN, null, false, "2026-10-25"))
            close()
        }
        val reopened = open()
        assertEquals("BBVA", reopened.catalogDao().account("a1")?.name)
        reopened.close()
    }
}
