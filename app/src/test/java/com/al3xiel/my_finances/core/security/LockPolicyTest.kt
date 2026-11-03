package com.al3xiel.my_finances.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {

    private val minute = 60_000L

    @Test
    fun coldStartLocksWhenEnabled() {
        assertTrue(LockPolicy.shouldLock(lockEnabled = true, autoLockMinutes = 5, backgroundedAtMillis = null, nowMillis = 10))
    }

    @Test
    fun neverLocksWhenDisabled() {
        assertFalse(LockPolicy.shouldLock(lockEnabled = false, autoLockMinutes = 0, backgroundedAtMillis = null, nowMillis = 10))
        assertFalse(LockPolicy.shouldLock(lockEnabled = false, autoLockMinutes = 0, backgroundedAtMillis = 0, nowMillis = 99 * minute))
    }

    @Test
    fun zeroMinutesLocksAlways() {
        assertTrue(LockPolicy.shouldLock(true, 0, backgroundedAtMillis = 1_000, nowMillis = 1_000))
    }

    @Test
    fun staysUnlockedBeforeTheLimit() {
        assertFalse(LockPolicy.shouldLock(true, 5, backgroundedAtMillis = 0, nowMillis = 5 * minute - 1))
    }

    @Test
    fun locksAtTheLimit() {
        assertTrue(LockPolicy.shouldLock(true, 5, backgroundedAtMillis = 0, nowMillis = 5 * minute))
    }

    @Test
    fun locksWhenClockWentBackwards() {
        assertTrue(LockPolicy.shouldLock(true, 5, backgroundedAtMillis = 10 * minute, nowMillis = minute))
    }
}
