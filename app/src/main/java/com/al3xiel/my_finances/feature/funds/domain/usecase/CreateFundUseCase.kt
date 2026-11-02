package com.al3xiel.my_finances.feature.funds.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.FundCreated
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import java.time.LocalDate
import javax.inject.Inject

/** Solo el modo estimado lleva estimado diario (mayor a cero); en los demás modos se descarta. */
internal fun dailyEstimateFor(mode: ConsumptionMode, dailyEstimate: Money?): Outcome<Money?> = when {
    mode != ConsumptionMode.DAILY_ESTIMATE -> Outcome.Ok(null)
    dailyEstimate == null || !dailyEstimate.isPositive -> Outcome.Failed(BusinessError.InvalidAmount)
    else -> Outcome.Ok(dailyEstimate)
}

class CreateFundUseCase @Inject constructor(
    private val funds: FundRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        name: String,
        mode: ConsumptionMode,
        dailyEstimate: Money?,
        startsOn: LocalDate,
        endsOn: LocalDate?,
    ): Outcome<FundId> {
        val clean = name.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (NameNormalizer.isTaken(funds.activeNames(), clean)) return Outcome.Failed(BusinessError.DuplicateName)
        if (endsOn != null && endsOn.isBefore(startsOn)) return Outcome.Failed(BusinessError.DateOutOfFundRange)
        val estimate = when (val result = dailyEstimateFor(mode, dailyEstimate)) {
            is Outcome.Failed -> return result
            is Outcome.Ok -> result.value
        }

        val id = FundId(ids.newId())
        recorder.record(FundCreated(id, clean, mode, estimate, startsOn, endsOn), startsOn)
        return Outcome.Ok(id)
    }
}
