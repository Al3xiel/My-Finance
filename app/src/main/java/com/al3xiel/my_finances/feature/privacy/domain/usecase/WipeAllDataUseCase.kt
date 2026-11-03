package com.al3xiel.my_finances.feature.privacy.domain.usecase

import com.al3xiel.my_finances.core.di.IoDispatcher
import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class WipeResult(val failedSteps: List<String>) {
    val isComplete: Boolean get() = failedSteps.isEmpty()
}

class WipeAllDataUseCase @Inject constructor(
    private val steps: @JvmSuppressWildcards Set<WipeStep>,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /** Si un paso falla sigue con los demás: borrar de más es mejor que dejar datos a medias. */
    suspend operator fun invoke(): WipeResult = withContext(dispatcher) {
        val failed = mutableListOf<String>()
        for (step in steps.sortedBy { it.priority }) {
            try {
                step.wipe()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed += step.id
            }
        }
        WipeResult(failed)
    }
}
