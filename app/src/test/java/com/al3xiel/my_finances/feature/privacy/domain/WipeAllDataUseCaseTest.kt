package com.al3xiel.my_finances.feature.privacy.domain

import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import com.al3xiel.my_finances.feature.privacy.domain.usecase.WipeAllDataUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WipeAllDataUseCaseTest {

    private val order = mutableListOf<String>()

    private fun step(stepId: String, stepPriority: Int = WipeStep.DEFAULT_PRIORITY, fail: Boolean = false) =
        object : WipeStep {
            override val id = stepId
            override val priority = stepPriority
            override suspend fun wipe() {
                order += stepId
                if (fail) error("boom")
            }
        }

    private fun useCase(vararg steps: WipeStep) = WipeAllDataUseCase(steps.toSet(), UnconfinedTestDispatcher())

    @Test
    fun runsTheKeyFirstThenTheRestByPriority() = runTest {
        useCase(
            step("cache", WipeStep.CACHE_PRIORITY),
            step("other"),
            step("key", WipeStep.KEY_PRIORITY),
            step("databases", WipeStep.DATABASES_PRIORITY),
            step("preferences", WipeStep.PREFERENCES_PRIORITY),
        )()
        assertEquals(listOf("key", "databases", "preferences", "cache", "other"), order)
    }

    @Test
    fun aFailingStepDoesNotStopTheOthersAndIsReported() = runTest {
        val result = useCase(
            step("key", WipeStep.KEY_PRIORITY, fail = true),
            step("databases", WipeStep.DATABASES_PRIORITY),
            step("cache", WipeStep.CACHE_PRIORITY, fail = true),
        )()
        assertEquals(listOf("key", "databases", "cache"), order)
        assertEquals(listOf("key", "cache"), result.failedSteps)
        assertTrue(!result.isComplete)
    }

    @Test
    fun reportsCompleteWhenEverythingWorked() = runTest {
        assertTrue(useCase(step("key", WipeStep.KEY_PRIORITY), step("cache"))().isComplete)
    }
}
