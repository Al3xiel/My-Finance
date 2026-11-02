package com.al3xiel.my_finances.core.di

import android.content.Context
import androidx.room.Room
import com.al3xiel.my_finances.core.security.DatabaseKeyProvider
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.database.migration.MIGRATION_1_2
import com.al3xiel.my_finances.data.database.migration.MIGRATION_2_3
import com.al3xiel.my_finances.data.database.migration.MIGRATION_3_4
import com.al3xiel.my_finances.data.database.migration.MIGRATION_4_5
import com.al3xiel.my_finances.data.database.migration.MIGRATION_5_6
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.ProjectionStateDao
import com.al3xiel.my_finances.data.projection.ProjectionVersionsImpl
import com.al3xiel.my_finances.data.projection.ProjectionRebuilderImpl
import com.al3xiel.my_finances.data.projection.catalog.CatalogDao
import com.al3xiel.my_finances.data.projection.catalog.CatalogProjector
import com.al3xiel.my_finances.data.projection.catalog.CatalogQueryDao
import com.al3xiel.my_finances.data.projection.movement.LedgerDao
import com.al3xiel.my_finances.data.projection.movement.LedgerQueryDao
import com.al3xiel.my_finances.data.projection.movement.MovementDao
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.data.projection.movement.MovementQueryDao
import com.al3xiel.my_finances.data.projection.expectedincome.ExpectedIncomeDao
import com.al3xiel.my_finances.data.projection.expectedincome.ExpectedIncomeProjector
import com.al3xiel.my_finances.data.projection.expectedincome.ExpectedIncomeQueryDao
import com.al3xiel.my_finances.data.projection.fund.FundDao
import com.al3xiel.my_finances.data.projection.fund.FundProjector
import com.al3xiel.my_finances.data.projection.fund.FundQueryDao
import com.al3xiel.my_finances.data.projection.loan.LoanDao
import com.al3xiel.my_finances.data.projection.loan.LoanProjector
import com.al3xiel.my_finances.data.projection.loan.LoanQueryDao
import com.al3xiel.my_finances.data.projection.pool.PersonQueryDao
import com.al3xiel.my_finances.data.projection.pool.PoolDao
import com.al3xiel.my_finances.data.projection.pool.PoolProjector
import com.al3xiel.my_finances.data.projection.pool.PoolQueryDao
import com.al3xiel.my_finances.data.projection.pool.ReimbursementDao
import com.al3xiel.my_finances.data.projection.pool.ReimbursementProjector
import com.al3xiel.my_finances.data.projection.reconciliation.ReconciliationDao
import com.al3xiel.my_finances.data.projection.origin.OriginDao
import com.al3xiel.my_finances.data.projection.origin.OriginProjector
import com.al3xiel.my_finances.data.projection.reconciliation.ReconciliationProjector
import com.al3xiel.my_finances.data.projection.reconciliation.ReconciliationQueryDao
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.usecase.ProjectionRebuilder
import com.al3xiel.my_finances.domain.usecase.ProjectionVersions
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
    abstract fun projectionVersions(impl: ProjectionVersionsImpl): ProjectionVersions

    @Binds
    @IntoSet
    abstract fun catalogProjector(impl: CatalogProjector): EventProjector

    @Binds
    @IntoSet
    abstract fun movementProjector(impl: MovementProjector): EventProjector

    @Binds
    @IntoSet
    abstract fun poolProjector(impl: PoolProjector): EventProjector

    @Binds
    @IntoSet
    abstract fun reimbursementProjector(impl: ReimbursementProjector): EventProjector

    @Binds
    @IntoSet
    abstract fun loanProjector(impl: LoanProjector): EventProjector

    @Binds
    @IntoSet
    abstract fun fundProjector(impl: FundProjector): EventProjector

    @Binds
    @IntoSet
    abstract fun expectedIncomeProjector(impl: ExpectedIncomeProjector): EventProjector

    @Binds
    @IntoSet
    abstract fun reconciliationProjector(impl: ReconciliationProjector): EventProjector

    @Binds
    @IntoSet
    abstract fun originProjector(impl: OriginProjector): EventProjector

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context, keyProvider: DatabaseKeyProvider): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(keyProvider.getOrCreate()))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(EventTriggersCallback)
                .build()
        }

        @Provides
        fun provideOriginDao(database: AppDatabase): OriginDao = database.originDao()

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
        fun providePoolDao(database: AppDatabase): PoolDao = database.poolDao()

        @Provides
        fun provideReimbursementDao(database: AppDatabase): ReimbursementDao = database.reimbursementDao()

        @Provides
        fun provideLoanDao(database: AppDatabase): LoanDao = database.loanDao()

        @Provides
        fun provideFundDao(database: AppDatabase): FundDao = database.fundDao()

        @Provides
        fun provideFundQueryDao(database: AppDatabase): FundQueryDao = database.fundQueryDao()

        @Provides
        fun provideProjectionStateDao(database: AppDatabase): ProjectionStateDao = database.projectionStateDao()

        @Provides
        fun provideReconciliationDao(database: AppDatabase): ReconciliationDao = database.reconciliationDao()

        @Provides
        fun provideReconciliationQueryDao(database: AppDatabase): ReconciliationQueryDao = database.reconciliationQueryDao()

        @Provides
        fun provideExpectedIncomeDao(database: AppDatabase): ExpectedIncomeDao = database.expectedIncomeDao()

        @Provides
        fun provideExpectedIncomeQueryDao(database: AppDatabase): ExpectedIncomeQueryDao = database.expectedIncomeQueryDao()

        @Provides
        fun providePoolQueryDao(database: AppDatabase): PoolQueryDao = database.poolQueryDao()

        @Provides
        fun providePersonQueryDao(database: AppDatabase): PersonQueryDao = database.personQueryDao()

        @Provides
        fun provideLoanQueryDao(database: AppDatabase): LoanQueryDao = database.loanQueryDao()

        @Provides
        @Named("appVersion")
        fun provideAppVersion(@ApplicationContext context: Context): String =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }
}
