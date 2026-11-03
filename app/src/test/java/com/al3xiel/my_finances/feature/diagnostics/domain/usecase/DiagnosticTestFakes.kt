package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.ProjectionSnapshotReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class FakeDiagnosticFiles : BackupFilePort {
    val files = mutableMapOf<String, ByteArray>()
    var failWrite = false
    var corruptOnRead = false
    val deleted = mutableListOf<String>()
    var opened = 0

    override suspend fun openOutput(uri: String): OutputStream {
        opened++
        val buffer = ByteArrayOutputStream()
        return object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(source: ByteArray, offset: Int, length: Int) {
                if (failWrite) throw IOException("disk full")
                buffer.write(source, offset, length)
            }
            override fun close() {
                files[uri] = buffer.toByteArray()
            }
        }
    }

    override suspend fun openInput(uri: String): InputStream? {
        val bytes = files[uri]?.copyOf() ?: return null
        if (corruptOnRead) bytes[bytes.size - 3] = (bytes[bytes.size - 3].toInt() xor 1).toByte()
        return ByteArrayInputStream(bytes)
    }

    override suspend fun sizeOf(uri: String): Long? = files[uri]?.size?.toLong()

    override suspend fun delete(uri: String): Boolean = files.remove(uri).also { deleted += uri } != null
}

internal class FakeSnapshots : ProjectionSnapshotReader {
    var includeOriginAsked: Boolean? = null
    override suspend fun read(accountId: AccountId?, reconciliationId: ReconciliationId?, includeOrigin: Boolean): PackageProjections {
        includeOriginAsked = includeOrigin
        return PackageProjections(mapOf("a1" to 1_000L), emptyList(), null, mapOf("ledger" to 1), null)
    }
}
