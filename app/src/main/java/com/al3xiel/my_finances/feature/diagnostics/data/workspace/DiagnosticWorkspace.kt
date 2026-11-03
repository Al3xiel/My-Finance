package com.al3xiel.my_finances.feature.diagnostics.data.workspace

import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageFormat
import com.al3xiel.my_finances.feature.diagnostics.data.PackageManifest
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.WorkspacePseudonymSource
import kotlinx.serialization.Serializable

/** Lo que se guarda del paquete abierto: su manifiesto, lo que decían sus proyecciones y el hash de su ZIP en claro. */
@Serializable
data class WorkspaceMeta(
    val manifest: PackageManifest,
    val projections: PackageProjections,
    val packageHash: String,
)

/**
 * Segunda base con el mismo esquema que la real, más dos archivos pequeños sellados con Keystore (D50, D51). Guarda un solo
 * paquete a la vez. Solo la escriben el importador, el aplicador de diccionario y el borrado; ninguna otra parte de la app
 * la abre. Estas funciones bloquean: quien llama las corre fuera del hilo principal.
 */
class DiagnosticWorkspace(
    private val files: WorkspaceFiles,
    private val keys: WorkspaceKeys,
    private val opener: (ByteArray) -> AppDatabase,
) : WorkspacePseudonymSource {
    private var database: AppDatabase? = null

    fun hasPackage(): Boolean = files.occupied()

    @Synchronized
    fun database(): AppDatabase = database ?: opener(keys.databaseKey()).also { database = it }

    /** Los seudónimos que aparecen en los eventos del paquete abierto (`Account-A`, `Person-1`, …), sin repetir. */
    override suspend fun pseudonyms(): List<String> =
        database().eventDao().all().flatMap { PSEUDONYM.findAll(it.payload).map { match -> match.value }.toList() }.distinct().sorted()

    fun save(meta: WorkspaceMeta) {
        val json = DiagnosticPackageFormat.json.encodeToString(WorkspaceMeta.serializer(), meta)
        files.writeAtomically(files.metaFile, keys.seal(json.toByteArray(Charsets.UTF_8)))
    }

    /** `null` si no hay paquete o si el archivo sellado no se puede abrir (alterado, o la clave se perdió). */
    fun meta(): WorkspaceMeta? = runCatching {
        val sealed = files.metaFile.takeIf { it.exists() }?.readBytes() ?: return null
        DiagnosticPackageFormat.json.decodeFromString(WorkspaceMeta.serializer(), String(keys.unseal(sealed), Charsets.UTF_8))
    }.getOrNull()

    fun saveDictionary(plain: ByteArray) = files.writeAtomically(files.dictionaryFile, keys.seal(plain))

    fun dictionary(): ByteArray? = runCatching {
        files.dictionaryFile.takeIf { it.exists() }?.let { keys.unseal(it.readBytes()) }
    }.getOrNull()

    /** Cierra la base y borra archivo, `-wal`/`-shm`, clave, alias y los dos archivos pequeños. Repetirlo no hace daño. */
    @Synchronized
    fun delete() {
        runCatching { database?.close() }
        database = null
        files.deleteAll()
        keys.delete()
    }

    private companion object {
        val PSEUDONYM = Regex("""(?:Account-[A-Z]+|Person-\d+|Category-\d+|Tag-\d+|Source-\d+|Pool-\d+|Fund-\d+)""")
    }
}
