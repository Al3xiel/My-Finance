package com.al3xiel.my_finances.core.di

import android.content.Context
import android.os.Build
import androidx.room.Room
import com.al3xiel.my_finances.core.security.AndroidKeystoreKeyWrapper
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageBuilder
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageImporter
import com.al3xiel.my_finances.feature.diagnostics.data.PdfDiagnosticSummaryRenderer
import com.al3xiel.my_finances.feature.diagnostics.data.RoomProjectionSnapshotReader
import com.al3xiel.my_finances.feature.diagnostics.data.RoomTableFingerprintSource
import com.al3xiel.my_finances.feature.diagnostics.data.RoomWorkspaceReplayer
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspace
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspaceWipeStep
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceEventSink
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.WorkspaceEventWriter
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceFiles
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceKeys
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticPackageReader
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticPackageWriter
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticSummaryRenderer
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticWorkspaceStore
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.ProjectionSnapshotReader
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.TableFingerprintSource
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.WorkspacePseudonymSource
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.WorkspaceReplayer
import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import java.security.SecureRandom
import javax.inject.Named
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

private const val WORKSPACE_KEY_ALIAS = "diagnostic_workspace_wrapper"
private const val WORKSPACE_KEY_FILE = "diagnostic.key"
private const val WORKSPACE_DATABASE = "diagnostic-workspace.db"

@Module
@InstallIn(SingletonComponent::class)
interface DiagnosticsBindingsModule {
    @Binds
    fun snapshotReader(impl: RoomProjectionSnapshotReader): ProjectionSnapshotReader

    @Binds
    fun summaryRenderer(impl: PdfDiagnosticSummaryRenderer): DiagnosticSummaryRenderer

    @Binds
    fun tableFingerprintSource(impl: RoomTableFingerprintSource): TableFingerprintSource

    @Binds
    fun packageWriter(impl: DiagnosticPackageBuilder): DiagnosticPackageWriter

    @Binds
    fun packageReader(impl: DiagnosticPackageImporter): DiagnosticPackageReader

    @Binds
    fun workspaceReplayer(impl: RoomWorkspaceReplayer): WorkspaceReplayer

    @Binds
    fun workspaceEventWriter(impl: WorkspaceEventSink): WorkspaceEventWriter

    /** El borrado total también elimina el espacio de diagnóstico (D51). */
    @Binds
    @IntoSet
    fun workspaceWipeStep(impl: DiagnosticWorkspaceWipeStep): WipeStep
}

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsProvidesModule {
    @Provides
    @Named("androidVersion")
    fun androidVersion(): String = Build.VERSION.RELEASE

    @Provides
    @Singleton
    fun workspaceFiles(@ApplicationContext context: Context): WorkspaceFiles =
        WorkspaceFiles(requireNotNull(context.getDatabasePath(WORKSPACE_DATABASE).absoluteFile.parentFile))

    /** Alias y archivo propios: borrar el espacio nunca toca la clave de la base real (D51). */
    @Provides
    @Singleton
    fun workspaceKeys(@ApplicationContext context: Context): WorkspaceKeys =
        WorkspaceKeys(AndroidKeystoreKeyWrapper(WORKSPACE_KEY_ALIAS), File(context.noBackupFilesDir, WORKSPACE_KEY_FILE), SecureRandom())

    @Provides
    fun workspaceStore(workspace: DiagnosticWorkspace): DiagnosticWorkspaceStore = workspace

    @Provides
    fun pseudonymSource(workspace: DiagnosticWorkspace): WorkspacePseudonymSource = workspace

    @Provides
    @Singleton
    fun diagnosticWorkspace(@ApplicationContext context: Context, files: WorkspaceFiles, keys: WorkspaceKeys): DiagnosticWorkspace =
        DiagnosticWorkspace(files, keys) { key ->
            System.loadLibrary("sqlcipher")
            Room.databaseBuilder(context, AppDatabase::class.java, WORKSPACE_DATABASE)
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addCallback(EventTriggersCallback)
                .build()
        }
}
