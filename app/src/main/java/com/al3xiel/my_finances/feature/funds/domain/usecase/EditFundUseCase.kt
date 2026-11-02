package com.al3xiel.my_finances.feature.funds.domain.usecase

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.FundEdited
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.rules.FundEdit
import com.al3xiel.my_finances.feature.funds.domain.rules.FundRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import java.time.LocalDate
import javax.inject.Inject

/** Guarda el estado completo del fondo; el modo y el inicio se bloquean según lo que ya pasó en él (T3). */
class EditFundUseCase @Inject constructor(
    private val funds: FundRepository,
    private val recorder: EventRecorder,
    private val lock: MovementWriteLock,
) {
    suspend operator fun invoke(
        id: FundId,
        name: String,
        mode: ConsumptionMode,
        dailyEstimate: Money?,
        startsOn: LocalDate,
        endsOn: LocalDate?,
    ): Outcome<Unit> = lock.withLock { execute(id, name, mode, dailyEstimate, startsOn, endsOn) }

    private suspend fun execute(
        id: FundId,
        name: String,
        mode: ConsumptionMode,
        dailyEstimate: Money?,
        startsOn: LocalDate,
        endsOn: LocalDate?,
    ): Outcome<Unit> {
        val current = funds.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        val terms = FundRules.termsOf(current)
        val facts = funds.editFacts(id)
        val locks = FundRules.editLocks(facts.hasUsageDays, facts.hasCounts, facts.hasAssignedExpenses, facts.latestActivityOn)
        FundRules.checkEdit(terms, FundEdit(mode, startsOn, endsOn), locks)?.let { return Outcome.Failed(it) }

        val clean = name.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (NameNormalizer.isTaken(funds.activeNames() - current.fund.name, clean)) return Outcome.Failed(BusinessError.DuplicateName)
        if (endsOn != null && endsOn.isBefore(startsOn)) return Outcome.Failed(BusinessError.DateOutOfFundRange)
        val estimate = when (val result = dailyEstimateFor(mode, dailyEstimate)) {
            is Outcome.Failed -> return Outcome.Failed(result.error)
            is Outcome.Ok -> result.value
        }

        recorder.record(FundEdited(id, clean, mode, estimate, startsOn, endsOn))
        return Outcome.Ok(Unit)
    }
}
