package com.al3xiel.my_finances.core.security

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AndroidKeystoreKeyWrapperTest {

    private val wrapper = AndroidKeystoreKeyWrapper(alias = "test_wrap_key")

    @After
    fun tearDown() = wrapper.deleteKey()

    @Test
    fun roundTrips() {
        val plain = ByteArray(32) { it.toByte() }
        assertArrayEquals(plain, wrapper.unwrap(wrapper.wrap(plain)))
    }

    @Test
    fun wrappedBytesDifferFromPlainAndFromEachOther() {
        val plain = ByteArray(32) { it.toByte() }
        val a = wrapper.wrap(plain)
        val b = wrapper.wrap(plain)
        assertFalse(a.contentEquals(plain))
        assertFalse(a.contentEquals(b))
    }

    @Test(expected = Exception::class)
    fun cannotUnwrapAfterTheKeyIsDeleted() {
        val wrapped = wrapper.wrap(ByteArray(32))
        wrapper.deleteKey()
        wrapper.unwrap(wrapped)
    }
}
