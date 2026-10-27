package com.al3xiel.my_finances.core.security

import java.io.File
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseKeyProviderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class XorKeyWrapper(var broken: Boolean = false) : KeyWrapper {
        var keyDeleted = 0

        override fun wrap(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

        override fun unwrap(wrapped: ByteArray): ByteArray {
            check(!broken) { "cannot unwrap" }
            return wrap(wrapped)
        }

        override fun deleteKey() {
            keyDeleted++
        }
    }

    private val wrapper = XorKeyWrapper()
    private fun keyFile() = File(folder.root, "db.key")
    private fun provider() = DatabaseKeyProvider(wrapper, keyFile(), SecureRandom())

    @Test
    fun generatesA256BitKey() {
        assertEquals(32, provider().getOrCreate().size)
    }

    @Test
    fun returnsTheSameKeyAcrossInstances() {
        assertArrayEquals(provider().getOrCreate(), provider().getOrCreate())
    }

    @Test
    fun neverStoresTheKeyInTheClear() {
        val key = provider().getOrCreate()
        assertTrue(keyFile().exists())
        assertFalse(keyFile().readBytes().contentEquals(key))
    }

    @Test
    fun deleteRemovesFileAndWrapperKeyAndNextKeyDiffers() {
        val provider = provider()
        val first = provider.getOrCreate()
        provider.delete()
        assertFalse(keyFile().exists())
        assertEquals(1, wrapper.keyDeleted)
        assertNotEquals(first.toList(), provider.getOrCreate().toList())
    }

    @Test
    fun deleteWithoutKeyDoesNotFail() {
        provider().delete()
    }

    @Test
    fun anUnreadableKeyIsNeverRegenerated() {
        provider().getOrCreate()
        wrapper.broken = true
        try {
            provider().getOrCreate()
            fail("should throw")
        } catch (_: IllegalStateException) {
            assertTrue(keyFile().exists())
        }
    }
}
