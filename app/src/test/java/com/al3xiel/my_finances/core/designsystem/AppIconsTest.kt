package com.al3xiel.my_finances.core.designsystem

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconsTest {
    private fun drawable(name: String) = File("src/main/res/drawable/$name.xml")

    @Test fun `every icon of the index exists as a vector resource`() {
        AppIcons.all.keys.forEach { name ->
            val file = drawable(name)
            assertTrue("$name falta en res/drawable", file.exists())
            val text = file.readText()
            assertTrue("$name no es un vector", text.contains("<vector"))
            assertTrue("$name no es de Material Symbols (viewport 960)", text.contains("android:viewportWidth=\"960\""))
        }
    }

    @Test fun `icons are distinct and tintable`() {
        assertEquals(AppIcons.all.size, AppIcons.all.values.toSet().size)
        AppIcons.all.keys.forEach { name ->
            assertTrue("$name debe poder teñirse (negro opaco)", drawable(name).readText().contains("android:fillColor=\"#FF000000\""))
        }
    }

    @Test fun `no drawable is left over from the old set`() {
        val used = AppIcons.all.keys + setOf("ic_notification", "ic_launcher_background", "ic_launcher_foreground")
        val icons = File("src/main/res/drawable").listFiles().orEmpty().map { it.nameWithoutExtension }.filter { it.startsWith("ic_") }
        assertEquals(emptyList<String>(), icons.filterNot { it in used })
    }
}
