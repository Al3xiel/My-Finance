package com.al3xiel.my_finances.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class LargestRemainderTest {
    @Test fun `sum of the shares is the exact total`() {
        val r = LargestRemainder.distribute(100, listOf("a" to 1L, "b" to 1L, "c" to 1L))
        assertEquals(100L, r.values.sum())
        assertEquals(mapOf("a" to 34L, "b" to 33L, "c" to 33L), r)
    }

    @Test fun `ties on the remainder go to the smallest id`() {
        val r = LargestRemainder.distribute(1, listOf("b" to 1L, "a" to 1L))
        assertEquals(mapOf("b" to 0L, "a" to 1L), r)
    }

    @Test fun `larger remainder wins over smaller id`() {
        val r = LargestRemainder.distribute(10, listOf("a" to 1L, "b" to 2L)) // 3.33 / 6.67
        assertEquals(mapOf("a" to 3L, "b" to 7L), r)
    }

    @Test fun `empty weights or zero weight give zeros`() {
        assertEquals(emptyMap<String, Long>(), LargestRemainder.distribute(5, emptyList()))
        assertEquals(mapOf("a" to 0L), LargestRemainder.distribute(5, listOf("a" to 0L)))
    }

    @Test fun `huge amounts do not overflow`() {
        val big = Long.MAX_VALUE / 2
        val r = LargestRemainder.distribute(big, listOf("a" to big, "b" to big))
        assertEquals(big, r.values.sum())
    }
}
