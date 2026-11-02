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

    private val declaredPermissions = manifest.getElementsByTagName("uses-permission").let { permissions ->
        (0 until permissions.length).map { (permissions.item(it) as Element).getAttributeNS(androidNs, "name") }
    }

    @Test
    fun manifestDoesNotDeclareInternet() {
        assertFalse(declaredPermissions.contains("android.permission.INTERNET"))
    }

    @Test
    fun onlyBiometricAndNotificationsPermissionsAreAllowed() {
        val allowed = setOf("android.permission.USE_BIOMETRIC", "android.permission.POST_NOTIFICATIONS")
        assertTrue("permisos fuera de la lista: $declaredPermissions", allowed.containsAll(declaredPermissions))
    }

    @Test
    fun notificationsPermissionIsDeclaredForTheReminders() {
        assertTrue(declaredPermissions.contains("android.permission.POST_NOTIFICATIONS"))
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

    @Test
    fun theOnlyProvidersAreTheShareFileProviderAndTheStartupOneThatDropsWorkManagerInit() {
        val providers = application.getElementsByTagName("provider")
        assertEquals(2, providers.length)
        val startup = (0 until providers.length).map { providers.item(it) as Element }
            .single { it.getAttributeNS(androidNs, "name") == "androidx.startup.InitializationProvider" }
        assertEquals("false", startup.getAttributeNS(androidNs, "exported"))
        val entries = startup.getElementsByTagName("meta-data")
        assertEquals(1, entries.length)
        assertEquals("androidx.work.WorkManagerInitializer", (entries.item(0) as Element).getAttributeNS(androidNs, "name"))
        assertEquals("remove", (entries.item(0) as Element).getAttributeNS("http://schemas.android.com/tools", "node"))

        val provider = (0 until providers.length).map { providers.item(it) as Element }
            .single { it.getAttributeNS(androidNs, "name") == "androidx.core.content.FileProvider" }
        assertEquals("\${applicationId}.share", provider.getAttributeNS(androidNs, "authorities"))
        assertEquals("false", provider.getAttributeNS(androidNs, "exported"))
        assertEquals("true", provider.getAttributeNS(androidNs, "grantUriPermissions"))
    }

    @Test
    fun shareFilePathsExposeOnlyTheShareFolderOfTheCache() {
        val paths = parse("src/main/res/xml/file_paths.xml")
        val children = (0 until paths.childNodes.length).map { paths.childNodes.item(it) }.filterIsInstance<Element>()
        assertEquals(listOf("cache-path"), children.map { it.tagName })
        assertEquals("share", children.single().getAttribute("name"))
        assertEquals("share/", children.single().getAttribute("path"))
    }

    private fun Element.excludesAllDomains(): Boolean {
        val excludes = getElementsByTagName("exclude")
        val domains = (0 until excludes.length).map { (excludes.item(it) as Element).getAttribute("domain") }
        return domains.containsAll(listOf("root", "file", "database", "sharedpref", "external"))
    }
}
