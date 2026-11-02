package com.al3xiel.my_finances.feature.backup.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.al3xiel.my_finances.core.di.IoDispatcher
import com.al3xiel.my_finances.core.security.DatabaseKeyProvider
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventEntity
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.domain.model.event.ChainRow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.IOException
import javax.inject.Inject

/** Staging con la misma clave y el mismo esquema (Room + triggers) que la base real, en un archivo aparte (D44). */
class RoomStagingSinkFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyProvider: DatabaseKeyProvider,
    private val files: DatabaseFiles,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : StagingSinkFactory {
    override suspend fun create(): StagingSink = withContext(dispatcher) {
        files.deleteStaging()
        System.loadLibrary("sqlcipher")
        val database = Room.databaseBuilder(context, AppDatabase::class.java, DatabaseFiles.STAGING)
            .openHelperFactory(SupportOpenHelperFactory(keyProvider.getOrCreate()))
            .addCallback(EventTriggersCallback)
            .build()
        RoomStagingSink(database, files, dispatcher)
    }
}

private class RoomStagingSink(
    private val database: AppDatabase,
    private val files: DatabaseFiles,
    private val dispatcher: CoroutineDispatcher,
) : StagingSink {
    override suspend fun insert(rows: List<ChainRow>) = withContext(dispatcher) {
        try {
            database.withTransaction {
                database.eventDao().insertRaw(rows.map { it.toEntity() })
            }
        } catch (e: RuntimeException) {
            throw IOException("could not write the staging database", e)
        }
    }

    override suspend fun commit() = withContext(dispatcher) {
        try {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            database.close()
        } catch (e: RuntimeException) {
            throw IOException("could not close the staging database", e)
        }
    }

    override suspend fun discard() = withContext(dispatcher) {
        runCatching { database.close() }
        files.deleteStaging()
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
