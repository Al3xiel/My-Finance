package com.al3xiel.my_finances.data.projection.fund

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

/** Escritura para el proyector de fondos; las consultas de pantalla vienen en T7. */
@Dao
interface FundDao {
    @Upsert suspend fun upsertFund(fund: FundEntity)

    @Query("SELECT * FROM funds WHERE id = :id") suspend fun fund(id: String): FundEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertUsageDay(day: FundUsageDayEntity)

    @Query("DELETE FROM fund_usage_days WHERE fund_id = :fundId AND day = :day") suspend fun deleteUsageDay(fundId: String, day: String)

    @Query("SELECT COUNT(*) FROM fund_usage_days WHERE fund_id = :fundId") suspend fun usageDayCount(fundId: String): Int

    @Insert suspend fun insertCount(count: FundCountEntity)

    @Insert suspend fun insertTopUp(topUp: FundTopUpEntity)

    @Query("DELETE FROM funds") suspend fun clearFunds()
    @Query("DELETE FROM fund_usage_days") suspend fun clearUsageDays()
    @Query("DELETE FROM fund_counts") suspend fun clearCounts()
    @Query("DELETE FROM fund_topups") suspend fun clearTopUps()
}
