package com.al3xiel.my_finances.feature.backup.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DatabaseFilesTest {
    @get:Rule val folder = TemporaryFolder()

    private fun file(name: String) = File(folder.root, name)

    private fun put(name: String, text: String) = file(name).writeText(text)

    private fun files(failOnMove: Int? = null): DatabaseFiles {
        var moves = 0
        return DatabaseFiles(folder.root) { from, to ->
            moves++
            if (moves == failOnMove) false else from.renameTo(to)
        }
    }

    @Test
    fun swapReplacesTheLiveDatabaseAndLeavesNoTemporaries() = runTest {
        put("finances.db", "old")
        put("finances.db-wal", "old-wal")
        put("finances-restore.db", "new")
        var closed = 0

        FileDatabaseSwapper(files()) { closed++ }.swap()

        assertEquals(1, closed)
        assertEquals("new", file("finances.db").readText())
        assertEquals(listOf("finances.db"), folder.root.list()!!.toList())
    }

    @Test
    fun aFailureMovingTheLiveDatabaseLeavesItUntouched() = runTest {
        put("finances.db", "old")
        put("finances-restore.db", "new")

        try {
            FileDatabaseSwapper(files(failOnMove = 1)) {}.swap()
            fail("expected a failure")
        } catch (_: IOException) {
        }

        assertEquals("old", file("finances.db").readText())
        assertFalse(file("finances-restore.db").exists())
    }

    @Test
    fun aFailureMovingTheStagingDatabaseRevertsToTheOldOne() = runTest {
        put("finances.db", "old")
        put("finances-restore.db", "new")

        try {
            FileDatabaseSwapper(files(failOnMove = 2)) {}.swap()
            fail("expected a failure")
        } catch (_: IOException) {
        }

        assertEquals("old", file("finances.db").readText())
        assertFalse(file("finances-restore.db").exists())
        assertFalse(file("finances.db.old").exists())
    }

    @Test
    fun recoverRestoresTheOldDatabaseWhenTheProcessDiedBetweenMoves() {
        put("finances.db.old", "old")
        put("finances-restore.db", "new")

        files().recover()

        assertEquals("old", file("finances.db").readText())
        assertFalse(file("finances-restore.db").exists())
    }

    @Test
    fun recoverDeletesTheOldDatabaseWhenTheSwapAlreadyHappened() {
        put("finances.db", "new")
        put("finances.db.old", "old")
        put("finances.db.old-wal", "w")

        files().recover()

        assertEquals("new", file("finances.db").readText())
        assertEquals(listOf("finances.db"), folder.root.list()!!.toList())
    }

    @Test
    fun recoverDropsAStaleStagingDatabase() {
        put("finances.db", "live")
        put("finances-restore.db", "stale")
        put("finances-restore.db-journal", "j")

        files().recover()

        assertEquals(listOf("finances.db"), folder.root.list()!!.toList())
    }

    @Test
    fun recoverWithNothingPendingChangesNothing() {
        put("finances.db", "live")

        files().recover()

        assertTrue(file("finances.db").exists())
        assertEquals(1, folder.root.list()!!.size)
    }
}
