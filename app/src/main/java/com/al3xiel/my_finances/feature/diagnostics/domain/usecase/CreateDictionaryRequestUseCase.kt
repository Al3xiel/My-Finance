package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticWorkspaceStore
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryRequest
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryRequestCodec
import javax.inject.Inject

sealed interface CreateRequestResult {
    data class Ready(val code: String) : CreateRequestResult
    data object NoPackage : CreateRequestResult
}

/** Arma el código de texto que se le envía a quien exportó; solo seudónimos y `eventId`, nunca nombres (D55). */
class CreateDictionaryRequestUseCase @Inject constructor(private val workspace: DiagnosticWorkspaceStore) {
    operator fun invoke(pseudonyms: List<String>, eventIds: List<String>): CreateRequestResult {
        val meta = workspace.meta() ?: return CreateRequestResult.NoPackage
        val request = DictionaryRequest(meta.manifest.exportId, meta.packageHash.take(PREFIX), pseudonyms.distinct().sorted(), eventIds.distinct().sorted())
        return CreateRequestResult.Ready(DictionaryRequestCodec.encode(request))
    }

    companion object {
        const val PREFIX = 12
    }
}
