package com.al3xiel.my_finances.feature.people.data

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.pool.PersonQueryDao
import com.al3xiel.my_finances.data.projection.pool.PoolQueryDao
import com.al3xiel.my_finances.data.projection.pool.toReceivable
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.model.PersonDetail
import com.al3xiel.my_finances.feature.people.domain.model.PersonLoanLine
import com.al3xiel.my_finances.feature.people.domain.model.PersonPartLine
import com.al3xiel.my_finances.feature.people.domain.model.ReimbursementLine
import com.al3xiel.my_finances.feature.people.domain.repository.PersonDetailRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

class PersonDetailRepositoryImpl @Inject constructor(
    private val pools: PoolQueryDao,
    private val queries: PersonQueryDao,
) : PersonDetailRepository {
    override suspend fun balanceMinor(personId: PersonId): Long = queries.balanceMinor(personId.value)

    override fun observePersonDetail(personId: PersonId): Flow<PersonDetail?> {
        val id = personId.value
        return combine(
            queries.observePerson(id),
            pools.observePoolsPending(),
            queries.observePersonLoans(id),
            queries.observeReimbursements(id),
            combine(queries.observePersonParts(id), queries.observePersonPartTags(id)) { parts, tags -> parts to tags },
        ) { person, allPools, loans, reimbursements, partsAndTags ->
            if (person == null) return@combine null
            val pools = allPools.filter { it.personId == id && it.totalPending > 0 }.map { it.toReceivable() }
            val loanLines = loans.map { PersonLoanLine(it.toReceivable(), it.status) }
            val openLoans = loanLines.filter { it.status == LoanStatus.OPEN }.sumOf { it.loan.pending.amountMinor }
            val (parts, tagRows) = partsAndTags
            val tags = tagRows.groupBy({ it.partId }, { it.tagName })
            PersonDetail(
                person = Person(PersonId(person.id), person.alias, person.isArchived),
                balance = pen(pools.sumOf { it.totalPending.amountMinor } + openLoans),
                pools = pools,
                loans = loanLines,
                reimbursements = reimbursements.map {
                    ReimbursementLine(MovementId(it.movementId), pen(it.amountMinor), LocalDate.parse(it.occurredOn), it.voided)
                },
                parts = parts.map {
                    PersonPartLine(
                        PartId(it.partId), it.poolName, LocalDate.parse(it.occurredOn), it.categoryName,
                        tags[it.partId].orEmpty(), pen(it.amount), pen(it.pending),
                    )
                },
            )
        }
    }
}
