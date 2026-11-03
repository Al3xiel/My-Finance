package com.al3xiel.my_finances.data.projection.fund

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.domain.model.event.FundClosed
import com.al3xiel.my_finances.domain.model.event.FundCounted
import com.al3xiel.my_finances.domain.model.event.FundCreated
import com.al3xiel.my_finances.domain.model.event.FundEdited
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.UsageDayMarked
import com.al3xiel.my_finances.domain.model.event.UsageDayUnmarked
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.rules.FundRules
import javax.inject.Inject

/**
 * Fondos, recargas, días de uso y arqueos. Los proyectores corren uno tras otro sobre todo el registro, así que este se
 * explica solo con los eventos del fondo y sus propias tablas: nunca lee `movement_parts` (el gastado detallado se
 * calcula al consultar, D23) y el sobrante de un cierre viene en el evento (D22).
 *
 * Un evento de un fondo que no existe se ignora a propósito: los casos de uso ya lo rechazan y, si llegara al registro,
 * el replay debe dar lo mismo que la proyección en vivo.
 */
class FundProjector @Inject constructor(
    private val funds: FundDao,
) : EventProjector {
    override val name = "fund"
    override val version = 1

    override suspend fun apply(event: StoredEvent) {
        val occurredOn = event.occurredOn.toString()
        when (val e = event.event) {
            is FundCreated -> funds.upsertFund(
                FundEntity(
                    e.fundId.value, e.name, e.consumptionMode, e.dailyEstimate?.amountMinor, e.startsOn.toString(),
                    e.endsOn?.toString(), allocatedMinor = 0, untrackedSpentMinor = 0, status = FundStatus.ACTIVE,
                    remainderPolicy = null, rolloverToFundId = null, closedOn = null,
                ),
            )

            is FundEdited -> funds.active(e.fundId.value)?.let {
                funds.upsertFund(
                    it.copy(
                        name = e.name, consumptionMode = e.consumptionMode, dailyEstimateMinor = e.dailyEstimate?.amountMinor,
                        startsOn = e.startsOn.toString(), endsOn = e.endsOn?.toString(),
                    ),
                )
            }

            is FundToppedUp -> funds.active(e.fundId.value)?.let {
                funds.upsertFund(it.copy(allocatedMinor = it.allocatedMinor + e.amount.amountMinor))
                funds.insertTopUp(
                    FundTopUpEntity("${event.sequence}-topup", it.id, e.amount.amountMinor, e.linkedIncomeMovementId?.value, occurredOn),
                )
            }

            is UsageDayMarked ->
                if (funds.fund(e.fundId.value) != null) funds.insertUsageDay(FundUsageDayEntity(e.fundId.value, e.day.toString()))

            is UsageDayUnmarked -> funds.deleteUsageDay(e.fundId.value, e.day.toString())

            is FundCounted -> funds.fund(e.fundId.value)?.let { count(event, it, e, occurredOn) }

            is FundClosed -> funds.active(e.fundId.value)?.let { close(it, e, occurredOn) }

            else -> Unit
        }
    }

    /** Un fondo cerrado ya no recibe recargas, ediciones ni traspasos: solo los casos de uso lo impedían. */
    private suspend fun FundDao.active(id: String) = fund(id)?.takeIf { it.status == FundStatus.ACTIVE }

    override suspend fun reset() {
        funds.clearTopUps()
        funds.clearCounts()
        funds.clearUsageDays()
        funds.clearFunds()
    }

    /** `real = asignado − declarado`; el estimado (días × estimado diario) y la desviación solo existen en modo estimado. */
    private suspend fun count(event: StoredEvent, fund: FundEntity, e: FundCounted, occurredOn: String) {
        val real = fund.allocatedMinor - e.declaredRemaining.amountMinor
        val estimated = FundRules.estimatedSpent(
            fund.consumptionMode,
            funds.usageDayCount(fund.id),
            fund.dailyEstimateMinor?.let { Money(it, CurrencyCode.PEN) },
        )?.amountMinor
        funds.insertCount(
            FundCountEntity(
                "${event.sequence}-count", fund.id, e.declaredRemaining.amountMinor, estimated, real, estimated?.let { real - it }, occurredOn,
            ),
        )
        e.untrackedSpend?.let { funds.upsertFund(fund.copy(untrackedSpentMinor = fund.untrackedSpentMinor + it.amount.amountMinor)) }
    }

    private suspend fun close(fund: FundEntity, e: FundClosed, occurredOn: String) {
        funds.upsertFund(
            fund.copy(
                status = FundStatus.CLOSED,
                remainderPolicy = e.remainderPolicy,
                rolloverToFundId = e.rollOverToFundId?.value,
                closedOn = occurredOn,
            ),
        )
        val target = e.rollOverToFundId?.value
        val moved = e.rolledOverAmount?.amountMinor
        if (e.remainderPolicy == RemainderPolicy.ROLL_OVER && target != null && moved != null) {
            funds.active(target)?.let { funds.upsertFund(it.copy(allocatedMinor = it.allocatedMinor + moved)) }
        }
    }
}
