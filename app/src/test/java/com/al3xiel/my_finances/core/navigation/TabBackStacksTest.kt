package com.al3xiel.my_finances.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabBackStacksTest {

    @Test
    fun `starts on Home with every tab at its root`() {
        val stacks = TabBackStacks()
        assertEquals(MainTab.HOME, stacks.selected)
        MainTab.entries.forEach { assertEquals(listOf(it.root), stacks.stackOf(it)) }
    }

    @Test
    fun `select keeps each tab stack`() {
        val stacks = TabBackStacks()
            .select(MainTab.MORE)
            .push(Settings)
            .select(MainTab.HOME)
            .select(MainTab.MORE)
        assertEquals(listOf(More, Settings), stacks.activeStack)
    }

    @Test
    fun `push adds to the active tab only`() {
        val stacks = TabBackStacks().select(MainTab.MORE).push(Privacy)
        assertEquals(listOf(More, Privacy), stacks.stackOf(MainTab.MORE))
        assertEquals(listOf(Home), stacks.stackOf(MainTab.HOME))
    }

    @Test
    fun `back pops a pushed route`() {
        val stacks = TabBackStacks().select(MainTab.MORE).push(Privacy).back()
        assertEquals(MainTab.MORE, stacks?.selected)
        assertEquals(listOf(More), stacks?.activeStack)
    }

    @Test
    fun `back at the root of another tab returns to Home`() {
        val stacks = TabBackStacks().select(MainTab.REPORTS).back()
        assertEquals(MainTab.HOME, stacks?.selected)
    }

    @Test
    fun `back at the root of Home exits`() {
        assertNull(TabBackStacks().back())
    }

    @Test
    fun `back from Home with a pushed route pops before exiting`() {
        val stacks = TabBackStacks().push(Settings).back()
        assertEquals(listOf(Home), stacks?.activeStack)
        assertNull(stacks?.back())
    }
}
