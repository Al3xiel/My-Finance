package com.al3xiel.my_finances.feature.pools.data

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.feature.pools.domain.model.SettlementStatementContent
import com.al3xiel.my_finances.feature.pools.domain.model.StatementLine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StatementRendererTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val renderer = StatementRenderer(context)
    private val shareDir = File(context.cacheDir, "share")

    @Before fun setUp() { shareDir.deleteRecursively() }

    @After fun tearDown() { shareDir.deleteRecursively() }

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun content(lines: Int = 3) = SettlementStatementContent(
        PoolId("dogs"), PoolPeriodId("dogs-p1"), "Perros – mamá", "Mamá", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
        (1..lines).map { StatementLine(PartId("p$it"), LocalDate.of(2026, 10, 1 + it % 28), "Mascotas", listOf("Perros"), pen(1_000), pen(1_000)) },
        pen(1_000L * lines), pen(0),
    )

    @Test
    fun rendersAPngInsideTheShareFolderOfTheCache() {
        val rendered = renderer.render(StatementId("s1"), content(), StatementFormat.IMAGE)

        val file = File(shareDir, "rendicion-s1.png")
        assertTrue(file.exists())
        assertEquals("image/png", rendered.mimeType)
        val bitmap = BitmapFactory.decodeFile(file.path)
        assertNotNull(bitmap)
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    @Test
    fun rendersAPdfAndPaginatesLongStatements() {
        renderer.render(StatementId("s2"), content(lines = 120), StatementFormat.PDF)

        val bytes = File(shareDir, "rendicion-s2.pdf").readBytes()
        assertEquals("%PDF", String(bytes.copyOfRange(0, 4)))
        val pages = Regex("/Type\\s*/Page\\b").findAll(String(bytes, Charsets.ISO_8859_1)).count()
        assertTrue("más de una página, hubo $pages", pages > 1)
    }

    @Test
    fun theFileNameDoesNotCarryNamesOrAmounts() {
        renderer.render(StatementId("s3"), content(), StatementFormat.PDF)

        assertEquals(listOf("rendicion-s3.pdf"), shareDir.list()!!.toList())
    }

    @Test
    fun theShareIntentGrantsOnlyReadAccessToTheProviderUri() {
        val rendered = renderer.render(StatementId("s4"), content(), StatementFormat.PDF)

        val chooser = renderer.shareIntent(rendered, "Compartir")
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!

        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        assertEquals("${context.packageName}.share", rendered.uri.authority)
        assertEquals(rendered.uri, send.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
    }
}
