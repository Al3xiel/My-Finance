package com.al3xiel.my_finances.feature.privacy.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWipeStepsTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theDatabasesStepDeletesEveryDatabase() = runBlocking {
        context.openOrCreateDatabase("wipe_test.db", Context.MODE_PRIVATE, null).close()
        assertTrue(context.databaseList().contains("wipe_test.db"))

        DatabasesWipeStep(context).wipe()

        assertFalse(context.databaseList().contains("wipe_test.db"))
    }

    @Test
    fun theCacheStepEmptiesTheCacheDirectoryIncludingSubfolders() = runBlocking {
        File(context.cacheDir, "share").apply { mkdirs() }.resolve("img.png").writeText("x")
        File(context.cacheDir, "top.tmp").writeText("x")

        CacheWipeStep(context).wipe()

        assertTrue(context.cacheDir.listFiles().orEmpty().isEmpty())
    }
}
