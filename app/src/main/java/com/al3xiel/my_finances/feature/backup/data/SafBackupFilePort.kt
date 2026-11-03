package com.al3xiel.my_finances.feature.backup.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.al3xiel.my_finances.core.di.IoDispatcher
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** Documento elegido con `CreateDocument` / `OpenDocument`: sin permiso persistente, sin registrar el `uri`. */
class SafBackupFilePort @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : BackupFilePort {
    override suspend fun openOutput(uri: String): OutputStream? = withContext(dispatcher) {
        runCatching { context.contentResolver.openOutputStream(Uri.parse(uri), "w") }.getOrNull()
    }

    override suspend fun openInput(uri: String): InputStream? = withContext(dispatcher) {
        runCatching { context.contentResolver.openInputStream(Uri.parse(uri)) }.getOrNull()
    }

    override suspend fun sizeOf(uri: String): Long? = withContext(dispatcher) {
        runCatching {
            context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    override suspend fun delete(uri: String): Boolean = withContext(dispatcher) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri)) }.getOrDefault(false)
    }
}
