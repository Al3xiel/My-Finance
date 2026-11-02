package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class ConsumptionMode { DETAILED, DAILY_ESTIMATE, NONE }

enum class RemainderPolicy { RELEASE_TO_AVAILABLE, ROLL_OVER }

/** Lo gastado del fondo sin anotar: el arqueo lo carga como gasto en esa cuenta (D11). `movementId` es el del gasto que se crea. */
@Serializable
data class UntrackedSpendData(
    val movementId: MovementId,
    val accountId: AccountId,
    val categoryId: CategoryId,
    val amount: Money,
)

@Serializable
data class FundCreated(
    val fundId: FundId,
    val name: String,
    val consumptionMode: ConsumptionMode,
    val dailyEstimate: Money?,
    @Serializable(with = LocalDateSerializer::class) val startsOn: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val endsOn: LocalDate?,
) : DomainEvent {
    override val type get() = "FundCreated"
    override val aggregateType get() = AggregateType.FUND
    override val aggregateId get() = fundId.value
}

/** El estado completo del fondo tras editarlo, no un diff. */
@Serializable
data class FundEdited(
    val fundId: FundId,
    val name: String,
    val consumptionMode: ConsumptionMode,
    val dailyEstimate: Money?,
    @Serializable(with = LocalDateSerializer::class) val startsOn: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val endsOn: LocalDate?,
) : DomainEvent {
    override val type get() = "FundEdited"
    override val aggregateType get() = AggregateType.FUND
    override val aggregateId get() = fundId.value
}

@Serializable
data class FundToppedUp(
    val fundId: FundId,
    val amount: Money,
    val linkedIncomeMovementId: MovementId?,
) : DomainEvent {
    override val type get() = "FundToppedUp"
    override val aggregateType get() = AggregateType.FUND
    override val aggregateId get() = fundId.value
}

@Serializable
data class UsageDayMarked(
    val fundId: FundId,
    @Serializable(with = LocalDateSerializer::class) val day: LocalDate,
) : DomainEvent {
    override val type get() = "UsageDayMarked"
    override val aggregateType get() = AggregateType.FUND
    override val aggregateId get() = fundId.value
}

@Serializable
data class UsageDayUnmarked(
    val fundId: FundId,
    @Serializable(with = LocalDateSerializer::class) val day: LocalDate,
) : DomainEvent {
    override val type get() = "UsageDayUnmarked"
    override val aggregateType get() = AggregateType.FUND
    override val aggregateId get() = fundId.value
}

@Serializable
data class FundCounted(
    val fundId: FundId,
    val declaredRemaining: Money,
    val untrackedSpend: UntrackedSpendData?,
) : DomainEvent {
    override val type get() = "FundCounted"
    override val aggregateType get() = AggregateType.FUND
    override val aggregateId get() = fundId.value
}

/** `rolledOverAmount` lo calcula el caso de uso: así el replay no depende de las partes de gasto (D22). */
@Serializable
data class FundClosed(
    val fundId: FundId,
    val remainderPolicy: RemainderPolicy,
    val rollOverToFundId: FundId?,
    val rolledOverAmount: Money?,
) : DomainEvent {
    override val type get() = "FundClosed"
    override val aggregateType get() = AggregateType.FUND
    override val aggregateId get() = fundId.value
}
