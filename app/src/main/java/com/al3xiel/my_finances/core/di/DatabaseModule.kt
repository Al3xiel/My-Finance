package com.al3xiel.my_finances.core.di

import android.content.Context
import androidx.room.Room
import com.al3xiel.my_finances.core.security.DatabaseKeyProvider
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.ProjectionRebuilderImpl
import com.al3xiel.my_finances.data.projection.catalog.CatalogDao
import com.al3xiel.my_finances.data.projection.catalog.CatalogProjector
import com.al3xiel.my_finances.data.projection.catalog.CatalogQueryDao
import com.al3xiel.my_finances.data.projection.movement.LedgerDao
import com.al3xiel.my_finances.data.projection.movement.LedgerQueryDao
import com.al3xiel.my_finances.data.projection.movement.MovementDao
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.data.projection.movement.MovementQueryDao
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.usecase.ProjectionRebuilder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Named
import javax.inject.Singleton

const val DATABASE_NAME = "finances.db"

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    @Binds
    abstract fun eventStore(impl: EventStoreImpl): EventStore

    @Binds
    abstract fun projectionRebuilder(impl: ProjectionRebuilderImpl): ProjectionRebuilder

    @Binds
    @IntoSet
    abstract fun catalogProjector(impl: CatalogProjector): EventProjector

    @Binds
    @IntoSet
    abstract fun movementProjector(impl: MovementProjector): EventProjector

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context, keyProvider: DatabaseKeyProvider): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(keyProvider.getOrCreate()))
                .addCallback(EventTriggersCallback)
                .build()
        }

        @Provides
        fun provideCatalogDao(database: AppDatabase): CatalogDao = database.catalogDao()

        @Provides
        fun provideMovementDao(database: AppDatabase): MovementDao = database.movementDao()

        @Provides
        fun provideLedgerDao(database: AppDatabase): LedgerDao = database.ledgerDao()

        @Provides
        fun provideCatalogQueryDao(database: AppDatabase): CatalogQueryDao = database.catalogQueryDao()

        @Provides
        fun provideMovementQueryDao(database: AppDatabase): MovementQueryDao = database.movementQueryDao()

        @Provides
        fun provideLedgerQueryDao(database: AppDatabase): LedgerQueryDao = database.ledgerQueryDao()

        @Provides
        @Named("appVersion")
        fun provideAppVersion(@ApplicationContext context: Context): String =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }
}
