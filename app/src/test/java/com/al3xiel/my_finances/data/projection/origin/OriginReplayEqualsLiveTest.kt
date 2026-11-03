package com.al3xiel.my_finances.data.projection.origin

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.movement.FakeLedgerDao
import com.al3xiel.my_finances.data.projection.movement.FakeMovementDao
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Mismos eventos, misma proyección: lo vivo (camino rápido y recálculos) iguala al replay, y Σ origen = saldo. */
class OriginReplayEqualsLiveTest {
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun history(seed: Int): List<DomainEvent> {
        val rnd = Random(seed)
        val accounts = listOf("A", "B", "C")
        val events = mutableListOf<DomainEvent>(
            *accounts.map { AccountCreated(AccountId(it), it, AccountType.BANK, CurrencyCode.PEN, null, pen(rnd.nextLong(0, 5_000))) }.toTypedArray(),
        )
        val active = linkedMapOf<String, MovementType>()
        repeat(80) { i ->
            val id = "m$i"
            val acc = accounts.random(rnd)
            val amount = rnd.nextLong(1, 20_000)
            when (rnd.nextInt(6)) {
                0, 1 -> { events += IncomeRegistered(MovementId(id), AccountId(acc), pen(amount), IncomeSourceId("s${rnd.nextInt(3)}"), null, null); active[id] = MovementType.INCOME }
                2 -> {
                    val cut = rnd.nextLong(0, amount)
                    events += ExpenseRegistered(
                        MovementId(id), AccountId(acc), pen(amount),
                        listOf(
                            ExpensePartData(PartId("$id-1"), pen(cut), CategoryId("c1"), emptyList(), null, null),
                            ExpensePartData(PartId("$id-2"), pen(amount - cut), CategoryId("c2"), emptyList(), null, null),
                        ).filter { it.amount.amountMinor > 0 },
                        null, null,
                    )
                    active[id] = MovementType.EXPENSE
                }
                3 -> events += TransferRegistered(MovementId(id), AccountId(acc), AccountId((accounts - acc).random(rnd)), pen(amount), null, null, null)
                4 -> active.keys.randomOrNull(rnd)?.let { events += MovementVoided(MovementId(it), "x", null); active.remove(it) }
                else -> active.entries.randomOrNull(rnd)?.takeIf { it.value == MovementType.INCOME }?.let {
                    events += MovementCorrected(MovementId(it.key), MovementSnapshot.Income(AccountId(acc), pen(amount), IncomeSourceId("s9"), null), "x", null)
                }
            }
        }
        return events
    }

    @Test fun `replay equals live and the mix of each account adds up to its balance`() = runTest {
        repeat(25) { seed ->
            val movementDao = FakeMovementDao()
            val ledgerDao = FakeLedgerDao()
            val dao = FakeOriginDao(movementDao, ledgerDao)
            val movement = MovementProjector(movementDao, ledgerDao)
            val origin = OriginProjector(dao)
            val log = history(seed).mapIndexed { i, e ->
                StoredEvent(i + 1L, "e$i", 1, "c", EventCodec.encode(e), LocalDate.of(2026, 10, 1), Instant.EPOCH, "t", "p", "h", e)
            }

            log.forEach { movement.apply(it); origin.apply(it) }
            val live = Triple(dao.balanceRows.toMap(), dao.attributionRows.toMap(), dao.cursorRow)
            val sums = dao.balanceRows.values.groupBy({ it.accountId }, { it.amountMinor }).mapValues { it.value.sum() }
            ledgerDao.entries.values.filter { !it.isVoided }.groupBy { it.accountId }.forEach { (a, rows) ->
                assertEquals("seed $seed account $a", rows.sumOf { it.deltaMinor }, sums[a] ?: 0L)
            }

            val projectors: List<EventProjector> = listOf(movement, origin)
            projectors.forEach { p ->
                p.reset()
                if (p === origin) origin.beginReplay()
                log.forEach { p.apply(it) }
                if (p === origin) origin.finishReplay()
            }
            assertEquals("seed $seed", live, Triple(dao.balanceRows.toMap(), dao.attributionRows.toMap(), dao.cursorRow))
        }
    }
}
