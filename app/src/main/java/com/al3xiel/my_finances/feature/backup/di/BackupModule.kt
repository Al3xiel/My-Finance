package com.al3xiel.my_finances.feature.backup.di

import android.content.Context
import com.al3xiel.my_finances.core.di.DATABASE_NAME
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.database.DATABASE_VERSION
import com.al3xiel.my_finances.domain.usecase.RestoreMarker
import com.al3xiel.my_finances.feature.backup.data.AndroidBackupReminderNotifier
import com.al3xiel.my_finances.feature.backup.data.BackupPreferences
import com.al3xiel.my_finances.feature.backup.data.BackupReminderWipeStep
import com.al3xiel.my_finances.feature.backup.data.DatabaseFiles
import com.al3xiel.my_finances.feature.backup.data.FileDatabaseSwapper
import com.al3xiel.my_finances.feature.backup.data.ProcessAppRestarter
import com.al3xiel.my_finances.feature.backup.data.RoomStagingSinkFactory
import com.al3xiel.my_finances.feature.backup.data.SafBackupFilePort
import com.al3xiel.my_finances.feature.backup.data.StagingSinkFactory
import com.al3xiel.my_finances.feature.backup.data.WorkManagerBackupReminderScheduler
import com.al3xiel.my_finances.feature.backup.domain.repository.AppRestarter
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderNotifier
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderScheduler
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import com.al3xiel.my_finances.feature.backup.domain.repository.DatabaseSwapper
import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface BackupBindingsModule {
    @Binds
    fun backupFilePort(impl: SafBackupFilePort): BackupFilePort

    @Binds
    fun settings(impl: BackupPreferences): BackupSettingsRepository

    @Binds
    fun restoreMarker(impl: BackupPreferences): RestoreMarker

    @Binds
    fun reminderNotifier(impl: AndroidBackupReminderNotifier): BackupReminderNotifier

    @Binds
    fun reminderScheduler(impl: WorkManagerBackupReminderScheduler): BackupReminderScheduler

    @Binds
    fun stagingSinkFactory(impl: RoomStagingSinkFactory): StagingSinkFactory

    @Binds
    fun appRestarter(impl: ProcessAppRestarter): AppRestarter

    /** Cancela el Worker antes de borrar la clave (prioridad menor que la de la clave). */
    @Binds
    @IntoSet
    fun reminderWipeStep(impl: BackupReminderWipeStep): WipeStep
}

@Module
@InstallIn(SingletonComponent::class)
object BackupProvidesModule {
    @Provides
    @Named("dbSchemaVersion")
    fun dbSchemaVersion(): Int = DATABASE_VERSION

    @Provides
    @Singleton
    fun databaseFiles(@ApplicationContext context: Context): DatabaseFiles =
        DatabaseFiles(requireNotNull(context.getDatabasePath(DATABASE_NAME).absoluteFile.parentFile))

    @Provides
    fun databaseSwapper(database: AppDatabase, files: DatabaseFiles): DatabaseSwapper =
        FileDatabaseSwapper(files) { database.close() }
}
