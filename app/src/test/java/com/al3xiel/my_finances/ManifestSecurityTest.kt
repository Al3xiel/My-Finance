package com.al3xiel.my_finances

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ManifestSecurityTest {

    private fun parse(path: String) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(path)).documentElement

    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val manifest = parse("src/main/AndroidManifest.xml")
    private val application = manifest.getElementsByTagName("application").item(0) as Element

    @Test
    fun allowBackupIsDisabled() {
        assertEquals("false", application.getAttributeNS(androidNs, "allowBackup"))
    }

    @Test
    fun applicationClassIsRegistered() {
        assertEquals(".MyFinancesApp", application.getAttributeNS(androidNs, "name"))
    }

    @Test
    fun manifestDoesNotDeclareInternet() {
        val permissions = manifest.getElementsByTagName("uses-permission")
        val names = (0 until permissions.length).map {
            (permissions.item(it) as Element).getAttributeNS(androidNs, "name")
        }
        assertFalse(names.contains("android.permission.INTERNET"))
    }

    @Test
    fun cloudBackupAndDeviceTransferExcludeEverything() {
        val rules = parse("src/main/res/xml/data_extraction_rules.xml")
        listOf("cloud-backup", "device-transfer").forEach { tag ->
            val section = rules.getElementsByTagName(tag).item(0) as Element
            assertTrue("$tag excluye todo", section.excludesAllDomains())
        }
    }

    @Test
    fun legacyFullBackupExcludesEverything() {
        assertTrue(parse("src/main/res/xml/backup_rules.xml").excludesAllDomains())
    }

    private fun Element.excludesAllDomains(): Boolean {
        val excludes = getElementsByTagName("exclude")
        val domains = (0 until excludes.length).map { (excludes.item(it) as Element).getAttribute("domain") }
        return domains.containsAll(listOf("root", "file", "database", "sharedpref", "external"))
    }
}
