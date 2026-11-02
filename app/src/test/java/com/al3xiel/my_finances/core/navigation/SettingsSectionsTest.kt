package com.al3xiel.my_finances.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSectionsTest {
    private fun section(id: String, order: Int) = SettingsSection(id, order) {}

    @Test
    fun noContributorsMeansNoSections() {
        assertTrue(SettingsSectionRegistry(emptySet()).sections.isEmpty())
    }

    @Test
    fun sectionsOfEveryFeatureAreMergedAndOrderedByOrderThenId() {
        val registry = SettingsSectionRegistry(
            setOf(
                SettingsSectionContributor { listOf(section("backup", 20), section("reminders", 10)) },
                SettingsSectionContributor { listOf(section("alpha", 20)) },
            ),
        )

        assertEquals(listOf("reminders", "alpha", "backup"), registry.sections.map { it.id })
    }
}
