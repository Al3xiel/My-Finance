package com.al3xiel.my_finances.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.al3xiel.my_finances.core.security.AndroidKeystoreKeyWrapper
import com.al3xiel.my_finances.core.security.Authenticator
import com.al3xiel.my_finances.core.security.BiometricAuthenticator
import com.al3xiel.my_finances.core.security.DatabaseKeyProvider
import com.al3xiel.my_finances.core.security.KeyWrapper
import com.al3xiel.my_finances.core.time.MonotonicClock
import com.al3xiel.my_finances.core.time.SystemMonotonicClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.security.SecureRandom
import javax.inject.Singleton

const val PREFERENCES_NAME = "preferences"

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> = context.preferencesStore
}

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    private const val KEY_ALIAS = "db_key_wrapper"
    private const val KEY_FILE = "db.key"

    @Provides
    @Singleton
    fun provideKeyWrapper(): KeyWrapper = AndroidKeystoreKeyWrapper(KEY_ALIAS)

    @Provides
    @Singleton
    fun provideDatabaseKeyProvider(@ApplicationContext context: Context, keyWrapper: KeyWrapper): DatabaseKeyProvider =
        DatabaseKeyProvider(keyWrapper, File(context.noBackupFilesDir, KEY_FILE), SecureRandom())
}

@Module
@InstallIn(SingletonComponent::class)
interface BindingsModule {
    @Binds
    fun authenticator(impl: BiometricAuthenticator): Authenticator

    @Binds
    fun monotonicClock(impl: SystemMonotonicClock): MonotonicClock
}

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
