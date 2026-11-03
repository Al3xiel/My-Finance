package com.al3xiel.my_finances.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreMenuRegistryTest {

    private fun contributor(vararg entries: MoreMenuEntry) = MoreMenuContributor { entries.toList() }

    @Test
    fun `without contributions the menu is empty`() {
        assertTrue(MoreMenuRegistry(emptySet()).entries.isEmpty())
    }

    @Test
    fun `entries from all contributors are sorted by order`() {
        val registry = MoreMenuRegistry(
            setOf(
                contributor(MoreMenuEntry("settings", 2, Settings, order = 90)),
                contributor(MoreMenuEntry("privacy", 1, Privacy, order = 80)),
            ),
        )
        assertEquals(listOf("privacy", "settings"), registry.entries.map { it.id })
    }
}
