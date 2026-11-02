package com.al3xiel.my_finances.feature.funds.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy
import java.time.LocalDate

data class Fund(
    val id: FundId,
    val name: String,
    val mode: ConsumptionMode,
    val dailyEstimate: Money?,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val allocated: Money,
    val untrackedSpent: Money,
    val status: FundStatus,
    val remainderPolicy: RemainderPolicy?,
    val rolloverToFundId: FundId?,
    val closedOn: LocalDate?,
)

/** Un fondo con lo que se calcula al consultar (D23): el gastado detallado sale de las partes de gastos activos. */
data class FundOverview(
    val fund: Fund,
    val detailedSpent: Money,
    val usageDays: Int,
    val lastCountOn: LocalDate?,
    /** Lo que aparta del Disponible libre; un fondo cerrado no aparta nada. */
    val reserved: Money,
    val estimatedSpent: Money?,
    val estimatedRemaining: Money,
)

data class FundSummary(val reserved: Money, val available: Money)

/** Qué hechos del fondo bloquean campos del editor. */
data class FundEditFacts(
    val hasUsageDays: Boolean,
    val hasCounts: Boolean,
    val hasAssignedExpenses: Boolean,
    /** Día más reciente con un día de uso o un gasto activo asignado: el fin del fondo no puede quedar antes. */
    val latestActivityOn: LocalDate? = null,
)

data class FundCountRecord(
    val declaredRemaining: Money,
    val realSpent: Money,
    val estimatedSpent: Money?,
    val deviation: Money?,
    val occurredOn: LocalDate,
)
