package com.al3xiel.my_finances.core.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SharedFilesCleanerTest {
    @get:Rule
    val cache = TemporaryFolder()

    private fun cleaner(dir: File) = SharedFilesCleaner(dir, Dispatchers.Unconfined)

    @Test
    fun removesEverythingInsideTheShareFolderAndKeepsTheFolder() = runTest {
        val share = cache.newFolder("share")
        File(share, "rendicion-1.pdf").writeText("x")
        File(share, "rendicion-2.png").writeText("y")
        File(share, "nested").apply { mkdir() }.let { File(it, "z.txt").writeText("z") }

        cleaner(share).clear()

        assertTrue(share.isDirectory)
        assertEquals(emptyList<String>(), share.list()!!.toList())
    }

    @Test
    fun leavesTheRestOfTheCacheAlone() = runTest {
        val share = cache.newFolder("share")
        File(share, "a.pdf").writeText("x")
        val other = File(cache.root, "image_cache.bin").apply { writeText("keep") }

        cleaner(share).clear()

        assertTrue(other.exists())
    }

    @Test
    fun aMissingFolderIsNotAnError() = runTest {
        val missing = File(cache.root, "share")

        cleaner(missing).clear()

        assertFalse(missing.exists())
    }
}
