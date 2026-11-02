package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class ExpectedIncomeScheduled(
    val expectedIncomeId: ExpectedIncomeId,
    val incomeSourceId: IncomeSourceId,
    val accountId: AccountId,
    val estimatedAmount: Money,
    @Serializable(with = LocalDateSerializer::class) val expectedFrom: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val expectedTo: LocalDate,
    val repeatsMonthly: Boolean,
) : DomainEvent {
    override val type get() = "ExpectedIncomeScheduled"
    override val aggregateType get() = AggregateType.EXPECTED_INCOME
    override val aggregateId get() = expectedIncomeId.value
}

/** Solo los campos presentes (no nulos) cambian; el proyector deja el resto como está. */
@Serializable
data class ExpectedIncomeEdited(
    val expectedIncomeId: ExpectedIncomeId,
    val incomeSourceId: IncomeSourceId?,
    val accountId: AccountId?,
    val estimatedAmount: Money?,
    @Serializable(with = LocalDateSerializer::class) val expectedFrom: LocalDate?,
    @Serializable(with = LocalDateSerializer::class) val expectedTo: LocalDate?,
    val repeatsMonthly: Boolean?,
) : DomainEvent {
    override val type get() = "ExpectedIncomeEdited"
    override val aggregateType get() = AggregateType.EXPECTED_INCOME
    override val aggregateId get() = expectedIncomeId.value
}

/** `nextExpectedIncomeId` es el siguiente que generó la repetición mensual (D28). */
@Serializable
data class ExpectedIncomeConfirmed(
    val expectedIncomeId: ExpectedIncomeId,
    val incomeMovementId: MovementId,
    val nextExpectedIncomeId: ExpectedIncomeId?,
) : DomainEvent {
    override val type get() = "ExpectedIncomeConfirmed"
    override val aggregateType get() = AggregateType.EXPECTED_INCOME
    override val aggregateId get() = expectedIncomeId.value
}

@Serializable
data class ExpectedIncomeCancelled(
    val expectedIncomeId: ExpectedIncomeId,
    val reason: String?,
) : DomainEvent {
    override val type get() = "ExpectedIncomeCancelled"
    override val aggregateType get() = AggregateType.EXPECTED_INCOME
    override val aggregateId get() = expectedIncomeId.value
}
