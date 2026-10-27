package com.al3xiel.my_finances.core.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class IdGeneratorTest {
    private val generator = UuidIdGenerator()

    @Test
    fun generatesVersion4Uuids() {
        val uuid = UUID.fromString(generator.newId())
        assertEquals(4, uuid.version())
    }

    @Test
    fun generatesDistinctIds() {
        assertNotEquals(generator.newId(), generator.newId())
    }
}
