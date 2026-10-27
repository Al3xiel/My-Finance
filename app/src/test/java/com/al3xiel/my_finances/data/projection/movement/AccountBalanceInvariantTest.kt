package com.al3xiel.my_finances.data.projection.movement

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
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
import com.al3xiel.my_finances.domain.model.event.FeeData
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/** Invariante contable: el saldo de cada cuenta es la suma de sus asientos no anulados, pase lo que pase. */
class AccountBalanceInvariantTest {
    private val accountIds = listOf("a", "b", "c").map(::AccountId)

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun script(seed: Int, size: Int): List<DomainEvent> {
        val random = Random(seed)
        val events = mutableListOf<DomainEvent>()
        accountIds.forEach { events += AccountCreated(it, it.value, AccountType.BANK, CurrencyCode.PEN, null, pen(random.nextLong(0, 50_000))) }
        val active = mutableMapOf<String, MovementType>()
        repeat(size) { n ->
            val id = "m$n"
            val account = accountIds.random(random)
            val amount = random.nextLong(1, 20_000)
            when (random.nextInt(6)) {
                0 -> { events += IncomeRegistered(MovementId(id), account, pen(amount), IncomeSourceId("s"), null, null); active[id] = MovementType.INCOME }
                1, 2 -> {
                    val parts = listOf(ExpensePartData(PartId("$id-1"), pen(amount), CategoryId("c"), emptyList(), null, null))
                    events += ExpenseRegistered(MovementId(id), account, pen(amount), parts, null, null)
                    active[id] = MovementType.EXPENSE
                }
                3 -> {
                    val to = (accountIds - account).random(random)
                    val fee = if (random.nextBoolean()) FeeData(pen(random.nextLong(1, 100)), CategoryId("fees")) else null
                    events += TransferRegistered(MovementId(id), account, to, pen(amount), fee, null, null)
                }
                4 -> active.keys.randomOrNull(random)?.let { target ->
                    events += MovementVoided(MovementId(target), "x", null)
                    active.remove(target)
                }
                else -> active.entries.randomOrNull(random)?.let { (target, type) ->
                    val snapshot = when (type) {
                        MovementType.INCOME -> MovementSnapshot.Income(account, pen(amount), IncomeSourceId("s"), null)
                        else -> MovementSnapshot.Expense(
                            account, pen(amount),
                            listOf(ExpensePartData(PartId("$target-1"), pen(amount), CategoryId("c"), emptyList(), null, null)),
                            null,
                        )
                    }
                    events += MovementCorrected(MovementId(target), snapshot, "x", null)
                }
            }
        }
        return events
    }

    @Test
    fun balanceEqualsSumOfNonVoidedEntriesForManyRandomHistories() = runTest {
        repeat(25) { seed ->
            val movements = FakeMovementDao()
            val ledger = FakeLedgerDao()
            val projector = MovementProjector(movements, ledger)
            script(seed, 60).forEachIndexed { i, event ->
                projector.apply(
                    StoredEvent(
                        i + 1L, "e$i", 1, "c", EventCodec.encode(event), LocalDate.of(2026, 10, 25),
                        Instant.ofEpochMilli(i.toLong()), "test", "p", "h", event,
                    ),
                )
            }
            accountIds.forEach { account ->
                val expected = ledger.entries.values.filter { it.accountId == account.value && !it.isVoided }.sumOf { it.deltaMinor }
                assertEquals("seed $seed account ${account.value}", expected, ledger.balances.getValue(account.value).balanceMinor)
            }
        }
    }
}
