package com.al3xiel.my_finances.feature.backup.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SafBackupFilePortTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val port = SafBackupFilePort(context, Dispatchers.IO)
    private val file = File(context.cacheDir, "port-test.fbak")
    private val uri get() = Uri.fromFile(file).toString()

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun whatIsWrittenIsReadBackByteForByte() = runBlocking {
        val bytes = ByteArray(200_000) { it.toByte() }

        port.openOutput(uri)!!.use { it.write(bytes) }

        assertArrayEquals(bytes, port.openInput(uri)!!.use { it.readBytes() })
    }

    @Test
    fun aMissingDocumentYieldsNullInsteadOfThrowing() = runBlocking {
        val missing = Uri.fromFile(File(context.cacheDir, "does-not-exist.fbak")).toString()

        assertNull(port.openInput(missing))
        assertNull(port.sizeOf(missing))
        assertFalse(port.delete(missing))
    }

    @Test
    fun anUnparsableUriYieldsNullInsteadOfThrowing() = runBlocking {
        assertNull(port.openInput("content://no.such.provider/doc/1"))
        assertNull(port.openOutput("content://no.such.provider/doc/1"))
    }
}
