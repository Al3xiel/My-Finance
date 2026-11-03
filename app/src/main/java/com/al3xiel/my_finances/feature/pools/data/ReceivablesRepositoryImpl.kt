package com.al3xiel.my_finances.feature.pools.data

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.pool.toReceivable
import com.al3xiel.my_finances.data.projection.pool.PoolQueryDao
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.feature.pools.domain.model.LoanReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PersonReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PoolDetail
import com.al3xiel.my_finances.feature.pools.domain.model.PoolReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.Receivables
import com.al3xiel.my_finances.feature.pools.domain.repository.ReceivablesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

class ReceivablesRepositoryImpl @Inject constructor(
    private val queries: PoolQueryDao,
) : ReceivablesRepository {
    override fun observeReceivables(): Flow<Receivables> =
        combine(queries.observePoolsPending(), queries.observeOpenLoans()) { poolRows, loanRows ->
            val pools = poolRows.filter { it.totalPending > 0 }.map { it.toReceivable() }
            val loans = loanRows.map { it.toReceivable() }
            val people = buildPeople(pools, loans)
            Receivables(pen(people.sumOf { it.total.amountMinor }), people, pools, loans)
        }

    override fun observePoolDetail(poolId: PoolId): Flow<PoolDetail?> =
        combine(queries.observePool(poolId.value), queries.observePeriods(poolId.value), queries.observePoolPending(poolId.value)) {
                pool, periods, pending ->
            if (pool == null || pending == null) {
                null
            } else {
                PoolDetail(pool.toDomain(), pending.alias, periods.map { it.toDomain() }, pen(pending.totalPending))
            }
        }

    private fun buildPeople(pools: List<PoolReceivable>, loans: List<LoanReceivable>): List<PersonReceivable> {
        val ids = (pools.map { it.responsiblePersonId } + loans.map { it.personId }).distinct()
        return ids.map { id ->
            val ownPools = pools.filter { it.responsiblePersonId == id }
            val ownLoans = loans.filter { it.personId == id }
            PersonReceivable(
                personId = id,
                alias = ownPools.firstOrNull()?.responsibleAlias ?: ownLoans.first().alias,
                poolsPending = pen(ownPools.sumOf { it.totalPending.amountMinor }),
                loansPending = pen(ownLoans.sumOf { it.pending.amountMinor }),
            )
        }.filter { it.total.isPositive }.sortedBy { it.alias.lowercase() }
    }
}
