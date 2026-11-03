package com.al3xiel.my_finances.data.projection

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.data.database.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectorSetFactoryTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun buildsEveryProjectorOnceWithDistinctNames() {
        val projectors = ProjectorSetFactory(database).create()

        assertEquals(
            setOf("catalog", "movement", "pool", "reimbursement", "loan", "fund", "expected_income", "reconciliation", "origin"),
            projectors.map { it.name }.toSet(),
        )
        assertEquals(projectors.size, projectors.map { it.name }.toSet().size)
    }

    @Test
    fun twoDatabasesGetIndependentProjectorInstances() {
        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val first = ProjectorSetFactory(database).create().associateBy { it.name }
            val second = ProjectorSetFactory(other).create().associateBy { it.name }

            first.forEach { (name, projector) -> assert(projector !== second.getValue(name)) { name } }
        } finally {
            other.close()
        }
    }
}
