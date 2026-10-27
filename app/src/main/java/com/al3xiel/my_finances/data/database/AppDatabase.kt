package com.al3xiel.my_finances.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.al3xiel.my_finances.data.eventstore.EventDao
import com.al3xiel.my_finances.data.eventstore.EventEntity
import com.al3xiel.my_finances.data.projection.ProjectionStateDao
import com.al3xiel.my_finances.data.projection.ProjectionStateEntity
import com.al3xiel.my_finances.data.projection.catalog.AccountEntity
import com.al3xiel.my_finances.data.projection.catalog.CatalogDao
import com.al3xiel.my_finances.data.projection.catalog.CatalogQueryDao
import com.al3xiel.my_finances.data.projection.catalog.CategoryEntity
import com.al3xiel.my_finances.data.projection.catalog.IncomeSourceEntity
import com.al3xiel.my_finances.data.projection.catalog.PersonEntity
import com.al3xiel.my_finances.data.projection.catalog.TagEntity
import com.al3xiel.my_finances.data.projection.movement.AccountBalanceEntity
import com.al3xiel.my_finances.data.projection.movement.LedgerDao
import com.al3xiel.my_finances.data.projection.movement.LedgerQueryDao
import com.al3xiel.my_finances.data.projection.movement.LedgerEntryEntity
import com.al3xiel.my_finances.data.projection.movement.MovementDao
import com.al3xiel.my_finances.data.projection.movement.MovementEntity
import com.al3xiel.my_finances.data.projection.movement.MovementPartEntity
import com.al3xiel.my_finances.data.projection.movement.MovementPartTagEntity
import com.al3xiel.my_finances.data.projection.movement.MovementQueryDao

/** Base cifrada con SQLCipher. Versión 1 sin `Migration`: las siguientes features agregan las suyas. */
@Database(
    entities = [
        EventEntity::class,
        ProjectionStateEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        PersonEntity::class,
        IncomeSourceEntity::class,
        MovementEntity::class,
        MovementPartEntity::class,
        MovementPartTagEntity::class,
        LedgerEntryEntity::class,
        AccountBalanceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun projectionStateDao(): ProjectionStateDao
    abstract fun catalogDao(): CatalogDao
    abstract fun movementDao(): MovementDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun catalogQueryDao(): CatalogQueryDao
    abstract fun movementQueryDao(): MovementQueryDao
    abstract fun ledgerQueryDao(): LedgerQueryDao
}
