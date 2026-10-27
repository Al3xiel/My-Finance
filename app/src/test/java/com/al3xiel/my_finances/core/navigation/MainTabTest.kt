package com.al3xiel.my_finances.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainTabTest {

    @Test
    fun `tabs are ordered Home Movements Receivables Reports More`() {
        assertEquals(
            listOf(Home, Movements, Receivables, Reports, More),
            MainTab.entries.map { it.root },
        )
    }

    @Test
    fun `each tab has distinct root, label and icon`() {
        assertEquals(5, MainTab.entries.map { it.root }.toSet().size)
        assertEquals(5, MainTab.entries.map { it.labelRes }.toSet().size)
        assertEquals(5, MainTab.entries.map { it.iconRes }.toSet().size)
    }

    @Test
    fun `fromRoot finds the tab that owns a root`() {
        MainTab.entries.forEach { assertEquals(it, MainTab.fromRoot(it.root)) }
    }

    @Test
    fun `fromRoot is null for a non-root route`() {
        assertTrue(MainTab.fromRoot(Privacy) == null)
    }

    @Test
    fun `only Home Movements and Receivables show the register button`() {
        assertEquals(
            listOf(MainTab.HOME, MainTab.MOVEMENTS, MainTab.RECEIVABLES),
            MainTab.entries.filter { it.showsRegisterFab },
        )
    }
}
