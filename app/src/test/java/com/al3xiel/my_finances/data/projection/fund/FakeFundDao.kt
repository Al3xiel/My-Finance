package com.al3xiel.my_finances.data.projection.fund

/** DAO en memoria: el proyector solo ve la interfaz, así que se prueba sin Room. */
class FakeFundDao : FundDao {
    val funds = linkedMapOf<String, FundEntity>()
    val usageDays = linkedSetOf<FundUsageDayEntity>()
    val counts = linkedMapOf<String, FundCountEntity>()
    val topUps = linkedMapOf<String, FundTopUpEntity>()

    override suspend fun upsertFund(fund: FundEntity) { funds[fund.id] = fund }
    override suspend fun fund(id: String) = funds[id]

    override suspend fun insertUsageDay(day: FundUsageDayEntity) { usageDays += day }
    override suspend fun deleteUsageDay(fundId: String, day: String) { usageDays.removeAll { it.fundId == fundId && it.day == day } }
    override suspend fun usageDayCount(fundId: String) = usageDays.count { it.fundId == fundId }

    override suspend fun insertCount(count: FundCountEntity) { counts[count.id] = count }
    override suspend fun insertTopUp(topUp: FundTopUpEntity) { topUps[topUp.id] = topUp }

    override suspend fun clearFunds() = funds.clear()
    override suspend fun clearUsageDays() = usageDays.clear()
    override suspend fun clearCounts() = counts.clear()
    override suspend fun clearTopUps() = topUps.clear()
}
