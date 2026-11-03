package com.al3xiel.my_finances.feature.quickentry.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {
    private val accepted = mapOf(
        "45" to 4500L,
        "12.50" to 1250L,
        "12,50" to 1250L,
        "12.5" to 1250L,
        "0.99" to 99L,
        "S/45" to 4500L,
        "s/45.20" to 4520L,
        "S/.45" to 4500L,
        "+50" to 5000L,
    )
    private val rejected = listOf("", "abc", "12.505", "1,000", "1.000", "1,234.50", "-5", "4 5", "45.", ".5", "1e3", "S/", "12,5,0")

    @Test fun `accepts the supported shapes`() =
        accepted.forEach { (text, minor) -> assertEquals(text, minor, AmountParser.parseMinor(text)) }

    @Test fun `rejects thousands, long decimals and noise`() =
        rejected.forEach { assertNull("«$it»", AmountParser.parseMinor(it)) }
}
