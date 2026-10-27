package com.al3xiel.my_finances.feature.pools.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.domain.model.event.SettlementStatementGenerated
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import com.al3xiel.my_finances.feature.pools.domain.model.GeneratedStatement
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import javax.inject.Inject

/**
 * Confirma la rendición (D17): emite `SettlementStatementGenerated`, que cierra el periodo de un cúmulo permanente.
 * Devuelve el contenido para renderizarlo y compartirlo después; compartir no emite nada.
 */
class GenerateSettlementStatementUseCase @Inject constructor(
    pools: PoolRepository,
    people: PersonRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val lock: MovementWriteLock,
) {
    private val prepare = PrepareSettlementStatementUseCase(pools, people, recorder)

    suspend operator fun invoke(poolId: PoolId, format: StatementFormat): Outcome<GeneratedStatement> =
        lock.withLock { execute(poolId, format) }

    private suspend fun execute(poolId: PoolId, format: StatementFormat): Outcome<GeneratedStatement> {
        val content = when (val result = prepare(poolId)) {
            is Outcome.Failed -> return result
            is Outcome.Ok -> result.value
        }
        val statementId = StatementId(ids.newId())
        recorder.record(
            SettlementStatementGenerated(
                statementId, content.poolId, content.periodId, content.lines.map { it.partId }, content.pendingTotal, format,
            ),
            content.generatedOn,
        )
        return Outcome.Ok(GeneratedStatement(statementId, content))
    }
}
