package com.al3xiel.my_finances.data.projection

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un proyector olvidado en la fábrica deja el espacio de diagnóstico con proyecciones incompletas (D50). Se lee el
 * código fuente porque la fábrica necesita Room para instanciarse: la instanciación la prueba `ProjectorSetFactoryTest`.
 */
class ProjectorSetFactoryCoverageTest {
    private val root = File("src/main/java/com/al3xiel/my_finances")
    private val declaration = Regex("""class\s+(\w+)\b[^{]*?:\s*EventProjector\b""", RegexOption.DOT_MATCHES_ALL)

    private fun projectorClasses(): List<String> = File(root, "data/projection").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file -> declaration.findAll(file.readText()).map { it.groupValues[1] } }
        .distinct()
        .sorted()
        .toList()

    @Test
    fun everyProjectorClassIsBuiltByTheFactory() {
        val factory = File(root, "data/projection/ProjectorSetFactory.kt")
        assertTrue("falta ProjectorSetFactory.kt", factory.exists())
        val source = factory.readText()
        val classes = projectorClasses()

        assertTrue("no se encontraron proyectores: ¿cambió la ruta?", classes.size >= 9)
        val missing = classes.filterNot { Regex("""\b$it\(""").containsMatchIn(source) }
        assertEquals("proyectores que la fábrica no arma", emptyList<String>(), missing)
    }

    @Test
    fun theDependencyInjectionModuleNoLongerListsProjectorsByHand() {
        val module = File(root, "core/di/DatabaseModule.kt").readText()

        assertFalse("DatabaseModule debe usar ProjectorSetFactory", Regex("""@IntoSet\s+abstract fun \w+\(impl: \w+\): EventProjector""").containsMatchIn(module))
        assertTrue(module.contains("ProjectorSetFactory"))
    }
}
