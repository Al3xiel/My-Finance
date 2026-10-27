package com.al3xiel.my_finances.feature.privacy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.al3xiel.my_finances.core.security.DatabaseKeyProvider
import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DatabaseKeyWipeStep @Inject constructor(
    private val keyProvider: DatabaseKeyProvider,
) : WipeStep {
    override val id = "database_key"
    override val priority = WipeStep.KEY_PRIORITY

    override suspend fun wipe() = keyProvider.delete()
}

class DatabasesWipeStep @Inject constructor(
    @ApplicationContext private val context: Context,
) : WipeStep {
    override val id = "databases"
    override val priority = WipeStep.DATABASES_PRIORITY

    override suspend fun wipe() {
        // Borrar una base arrastra su -journal/-wal, así que el resultado de cada llamada no sirve: se mira lo que queda.
        context.databaseList().forEach(context::deleteDatabase)
        val remaining = context.databaseList()
        check(remaining.isEmpty()) { "${remaining.size} database file(s) could not be deleted" }
    }
}

class PreferencesWipeStep @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : WipeStep {
    override val id = "preferences"
    override val priority = WipeStep.PREFERENCES_PRIORITY

    override suspend fun wipe() {
        dataStore.edit { it.clear() }
    }
}

class CacheWipeStep @Inject constructor(
    @ApplicationContext private val context: Context,
) : WipeStep {
    override val id = "cache"
    override val priority = WipeStep.CACHE_PRIORITY

    override suspend fun wipe() {
        val ok = context.cacheDir.listFiles().orEmpty().all { it.deleteRecursively() }
        check(ok) { "cache could not be fully deleted" }
    }
}
