package com.al3xiel.my_finances.core.security.container

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPasswordTest {

    private fun chars(text: String) = text.toCharArray()

    @Test
    fun tenCharactersTypedTwiceIsValid() {
        assertNull(BackupPassword.check(chars("0123456789"), chars("0123456789")))
    }

    @Test
    fun fewerThanTenCharactersIsTooShort() {
        assertEquals(BackupPasswordError.TOO_SHORT, BackupPassword.check(chars("012345678"), chars("012345678")))
        assertEquals(BackupPasswordError.TOO_SHORT, BackupPassword.check(chars(""), chars("")))
    }

    @Test
    fun differentConfirmationIsAMismatch() {
        assertEquals(BackupPasswordError.MISMATCH, BackupPassword.check(chars("0123456789"), chars("0123456780")))
    }

    @Test
    fun theLengthIsCheckedBeforeTheMatch() {
        assertEquals(BackupPasswordError.TOO_SHORT, BackupPassword.check(chars("short"), chars("other")))
    }

    @Test
    fun anExcessivelyLongPasswordIsRejected() {
        val long = CharArray(BackupPassword.MAX_LENGTH + 1) { 'a' }
        assertEquals(BackupPasswordError.TOO_LONG, BackupPassword.check(long, long.copyOf()))
    }

    @Test
    fun withoutConfirmationOnlyTheLengthIsChecked() {
        assertNull(BackupPassword.check(chars("0123456789")))
        assertEquals(BackupPasswordError.TOO_SHORT, BackupPassword.check(chars("abc")))
    }

    @Test
    fun wipeZeroesEveryArray() {
        val a = chars("0123456789")
        val b = chars("0123456789")
        BackupPassword.wipe(a, b)
        assertTrue(a.all { it == '\u0000' })
        assertTrue(b.all { it == '\u0000' })
    }
}
