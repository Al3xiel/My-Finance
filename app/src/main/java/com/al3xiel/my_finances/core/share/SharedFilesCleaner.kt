package com.al3xiel.my_finances.core.share

import android.content.Context
import com.al3xiel.my_finances.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Borra los archivos temporales que se crearon para compartir (rendiciones en imagen o PDF), docs/02 S7. Se llama al
 * iniciar la app y cuando la pantalla vuelve a primer plano tras el selector de apps.
 */
class SharedFilesCleaner(
    private val shareDir: File,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : this(File(context.cacheDir, SHARE_DIR), dispatcher)

    suspend fun clear() = withContext(dispatcher) {
        shareDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    companion object {
        /** Subcarpeta de `cacheDir` que expone `file_paths.xml`; nada más sale por el `FileProvider`. */
        const val SHARE_DIR = "share"
    }
}
