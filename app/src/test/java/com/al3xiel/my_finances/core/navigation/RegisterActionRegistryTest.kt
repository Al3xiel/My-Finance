package com.al3xiel.my_finances.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterActionRegistryTest {

    private fun contributor(vararg actions: RegisterAction) = RegisterActionContributor { actions.toList() }

    @Test
    fun `without contributions there are no actions`() {
        val registry = RegisterActionRegistry(emptySet())
        assertFalse(registry.hasActions)
        assertTrue(registry.actions.isEmpty())
    }

    @Test
    fun `actions from all contributors are sorted by order`() {
        val registry = RegisterActionRegistry(
            setOf(
                contributor(RegisterAction("transfer", 3, Settings, order = 30)),
                contributor(
                    RegisterAction("expense", 1, Privacy, order = 10),
                    RegisterAction("income", 2, More, order = 20),
                ),
            ),
        )
        assertTrue(registry.hasActions)
        assertEquals(listOf("expense", "income", "transfer"), registry.actions.map { it.id })
    }

    @Test
    fun `equal order falls back to id`() {
        val registry = RegisterActionRegistry(
            setOf(contributor(RegisterAction("b", 1, Privacy, 1), RegisterAction("a", 2, Settings, 1))),
        )
        assertEquals(listOf("a", "b"), registry.actions.map { it.id })
    }
}
