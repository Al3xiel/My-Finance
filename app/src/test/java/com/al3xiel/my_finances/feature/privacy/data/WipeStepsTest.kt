package com.al3xiel.my_finances.feature.privacy.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.al3xiel.my_finances.core.security.DatabaseKeyProvider
import com.al3xiel.my_finances.core.security.KeyWrapper
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WipeStepsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private object PlainKeyWrapper : KeyWrapper {
        override fun wrap(plain: ByteArray) = plain.reversedArray()
        override fun unwrap(wrapped: ByteArray) = wrapped.reversedArray()
        override fun deleteKey() = Unit
    }

    @Test
    fun theKeyStepRemovesTheStoredKey() = runTest {
        val keyFile = File(folder.root, "db.key")
        val provider = DatabaseKeyProvider(PlainKeyWrapper, keyFile, SecureRandom())
        provider.getOrCreate()
        assertTrue(keyFile.exists())

        DatabaseKeyWipeStep(provider).wipe()

        assertFalse(keyFile.exists())
    }

    @Test
    fun theKeyStepRunsFirst() {
        val steps = listOf(
            DatabaseKeyWipeStep(DatabaseKeyProvider(PlainKeyWrapper, File(folder.root, "k"), SecureRandom())),
        )
        assertEquals(0, steps.single().priority)
    }

    @Test
    fun thePreferencesStepClearsEverything() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        ) { File(folder.root, "prefs.preferences_pb") }
        store.edit { it[stringPreferencesKey("any")] = "value" }

        PreferencesWipeStep(store).wipe()

        assertTrue(store.data.first().asMap().isEmpty())
    }
}
