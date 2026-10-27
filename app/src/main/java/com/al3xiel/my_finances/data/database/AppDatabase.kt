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
import com.al3xiel.my_finances.data.projection.loan.LoanDao
import com.al3xiel.my_finances.data.projection.loan.LoanEntity
import com.al3xiel.my_finances.data.projection.loan.LoanQueryDao
import com.al3xiel.my_finances.data.projection.loan.LoanRepaymentEntity
import com.al3xiel.my_finances.data.projection.pool.PersonQueryDao
import com.al3xiel.my_finances.data.projection.pool.PoolDao
import com.al3xiel.my_finances.data.projection.pool.PoolEntity
import com.al3xiel.my_finances.data.projection.pool.PoolPeriodEntity
import com.al3xiel.my_finances.data.projection.pool.PoolQueryDao
import com.al3xiel.my_finances.data.projection.pool.ReimbursementAllocationEntity
import com.al3xiel.my_finances.data.projection.pool.ReimbursementDao
import com.al3xiel.my_finances.data.projection.pool.ReimbursementEntity
import com.al3xiel.my_finances.data.projection.pool.SettlementStatementEntity

/** Base cifrada con SQLCipher. Cada versión nueva trae su `Migration` en `migration/`. */
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
        PoolEntity::class,
        PoolPeriodEntity::class,
        SettlementStatementEntity::class,
        ReimbursementEntity::class,
        ReimbursementAllocationEntity::class,
        LoanEntity::class,
        LoanRepaymentEntity::class,
    ],
    version = 2,
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
    abstract fun poolDao(): PoolDao
    abstract fun reimbursementDao(): ReimbursementDao
    abstract fun loanDao(): LoanDao
    abstract fun poolQueryDao(): PoolQueryDao
    abstract fun personQueryDao(): PersonQueryDao
    abstract fun loanQueryDao(): LoanQueryDao
}
