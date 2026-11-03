package com.al3xiel.my_finances.feature.diagnostics.domain.model

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryRequestTest {
    private val request = DictionaryRequest("export-1", "abcdef012345", listOf("Account-A", "Person-1"), listOf("e4"))

    @Test
    fun roundTrips() {
        assertEquals(request, DictionaryRequestCodec.decode(DictionaryRequestCodec.encode(request)))
    }

    @Test
    fun isPlainTextSafeToPaste() {
        val code = DictionaryRequestCodec.encode(request)
        assertTrue(code.all { it.isLetterOrDigit() || it in "-_." })
    }

    @Test
    fun aCorruptedCodeIsRejected() {
        val code = DictionaryRequestCodec.encode(request)
        val flipped = code.replaceRange(5, 6, if (code[5] == 'A') "B" else "A")
        assertNull(DictionaryRequestCodec.decode(flipped))
        assertNull(DictionaryRequestCodec.decode(code.dropLast(1)))
        assertNull(DictionaryRequestCodec.decode(code.substringBefore('.')))
        assertNull(DictionaryRequestCodec.decode(""))
        assertNull(DictionaryRequestCodec.decode("not a code at all"))
    }

    @Test
    fun aCodeWithSurroundingWhitespaceStillWorks() {
        assertEquals(request, DictionaryRequestCodec.decode("  " + DictionaryRequestCodec.encode(request) + "\n"))
    }

    @Test
    fun theCodeCarriesNoNames() {
        val payload = DictionaryRequestCodec.encode(request).substringBefore('.')
        val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        assertTrue(json.contains("Account-A"))
        assertFalse(json.contains("Cuenta"))
    }
}
