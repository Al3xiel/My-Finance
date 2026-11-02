package com.al3xiel.my_finances.feature.funds.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.UsageDayMarked
import com.al3xiel.my_finances.domain.model.event.UsageDayUnmarked
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.rules.FundRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import java.time.LocalDate
import javax.inject.Inject

/** Marca o desmarca un día de uso; el evento ocurre ese día (`occurredOn`), no hoy. */
class MarkUsageDayUseCase @Inject constructor(
    private val funds: FundRepository,
    private val recorder: EventRecorder,
    private val lock: MovementWriteLock,
) {
    suspend operator fun invoke(id: FundId, day: LocalDate, marked: Boolean): Outcome<Unit> =
        lock.withLock { execute(id, day, marked) }

    private suspend fun execute(id: FundId, day: LocalDate, marked: Boolean): Outcome<Unit> {
        val fund = funds.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        FundRules.checkUsageDay(FundRules.termsOf(fund), day, recorder.today())?.let { return Outcome.Failed(it) }

        if (marked) {
            recorder.record(UsageDayMarked(id, day), day)
        } else {
            if (day !in funds.usageDays(id)) return Outcome.Failed(BusinessError.NotFound)
            recorder.record(UsageDayUnmarked(id, day), day)
        }
        return Outcome.Ok(Unit)
    }
}
