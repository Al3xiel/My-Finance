package com.al3xiel.my_finances.feature.funds.domain.rules

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FundRulesTest {
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private val oct1 = LocalDate.of(2026, 10, 1)
    private val oct31 = LocalDate.of(2026, 10, 31)

    private fun terms(
        status: FundStatus = FundStatus.ACTIVE,
        mode: ConsumptionMode = ConsumptionMode.DAILY_ESTIMATE,
        endsOn: LocalDate? = oct31,
        lastCountOn: LocalDate? = null,
    ) = FundTerms(status, mode, oct1, endsOn, lastCountOn)

    // ── Gasto no registrado del arqueo ──────────────────────────────────────────────────────

    @Test
    fun untrackedSpendIsWhatWasAllocatedMinusWhatIsAccountedForMinusWhatRemains() {
        // 100 − 30 detallado − 5 ya cargado − 40 que quedan = 25 sin anotar.
        val result = FundRules.untrackedSpend(pen(10_000), pen(3_000), pen(500), pen(4_000))

        assertEquals(Outcome.Ok(pen(2_500)), result)
    }

    @Test
    fun declaringExactlyWhatIsLeftMeansNothingUntracked() {
        assertEquals(Outcome.Ok(pen(0)), FundRules.untrackedSpend(pen(10_000), pen(3_000), pen(500), pen(6_500)))
    }

    @Test
    fun declaringMoreThanWhatIsLeftIsRejected() {
        val result = FundRules.untrackedSpend(pen(10_000), pen(3_000), pen(0), pen(7_001))

        assertEquals(Outcome.Failed(BusinessError.DeclaredAboveRemaining), result)
    }

    @Test
    fun aNegativeDeclaredBalanceIsRejected() {
        assertEquals(Outcome.Failed(BusinessError.NegativeAmount), FundRules.untrackedSpend(pen(10_000), pen(0), pen(0), pen(-1)))
    }

    // ── Apartado y restante estimado ────────────────────────────────────────────────────────

    @Test
    fun theReservedAmountOfAFundNeverGoesBelowZero() {
        assertEquals(pen(6_500), FundRules.reserved(pen(10_000), pen(3_000), pen(500)))
        assertEquals(pen(0), FundRules.reserved(pen(1_000), pen(1_200), pen(300)))
    }

    @Test
    fun withoutAnEstimateTheRemainingIsWhatIsNotAccountedFor() {
        listOf(ConsumptionMode.DETAILED, ConsumptionMode.NONE).forEach { mode ->
            assertEquals(pen(6_500), FundRules.estimatedRemaining(mode, pen(10_000), pen(3_000), pen(500), usageDays = 9, dailyEstimate = pen(500)))
        }
    }

    @Test
    fun theDailyEstimateCountsWhenItIsAboveWhatWasRegistered() {
        // 4 días × S/ 10 = 40 > 35 detallado + no registrado: restante 100 − 40 = 60.
        val remaining = FundRules.estimatedRemaining(ConsumptionMode.DAILY_ESTIMATE, pen(10_000), pen(3_000), pen(500), usageDays = 4, dailyEstimate = pen(1_000))

        assertEquals(pen(6_000), remaining)
    }

    @Test
    fun whatWasRegisteredCountsWhenItIsAboveTheDailyEstimate() {
        // 1 día × S/ 10 = 10 < 35 registrado: restante 100 − 35 = 65.
        val remaining = FundRules.estimatedRemaining(ConsumptionMode.DAILY_ESTIMATE, pen(10_000), pen(3_000), pen(500), usageDays = 1, dailyEstimate = pen(1_000))

        assertEquals(pen(6_500), remaining)
    }

    @Test
    fun estimatedSpentIsDaysTimesTheDailyEstimateOnlyInEstimateMode() {
        assertEquals(pen(3_000), FundRules.estimatedSpent(ConsumptionMode.DAILY_ESTIMATE, usageDays = 3, dailyEstimate = pen(1_000)))
        assertNull(FundRules.estimatedSpent(ConsumptionMode.DETAILED, usageDays = 3, dailyEstimate = pen(1_000)))
        assertNull(FundRules.estimatedSpent(ConsumptionMode.DAILY_ESTIMATE, usageDays = 3, dailyEstimate = null))
    }

    // ── Recargas ────────────────────────────────────────────────────────────────────────────

    @Test
    fun anOwnTopUpIsLimitedByTheFreeBalance() {
        assertNull(FundRules.checkOwnTopUp(pen(5_000), available = pen(5_000)))
        assertEquals(BusinessError.ExceedsAvailable, FundRules.checkOwnTopUp(pen(5_001), available = pen(5_000)))
        assertEquals(BusinessError.InvalidAmount, FundRules.checkOwnTopUp(pen(0), available = pen(5_000)))
    }

    @Test
    fun topUpsLinkedToAnIncomeAddUpToAtMostTheIncome() {
        assertNull(FundRules.checkIncomeTopUp(pen(2_000), incomeAmount = pen(5_000), alreadyToppedUp = pen(3_000)))
        assertEquals(BusinessError.ExceedsIncome, FundRules.checkIncomeTopUp(pen(2_001), incomeAmount = pen(5_000), alreadyToppedUp = pen(3_000)))
        assertEquals(BusinessError.InvalidAmount, FundRules.checkIncomeTopUp(pen(0), incomeAmount = pen(5_000), alreadyToppedUp = pen(0)))
    }

    // ── Fecha de un gasto con fondo ─────────────────────────────────────────────────────────

    @Test
    fun anExpenseNeedsAnActiveFundAndADateInsideItsRange() {
        assertNull(FundRules.checkExpenseDate(terms(), LocalDate.of(2026, 10, 15)))
        assertNull(FundRules.checkExpenseDate(terms(), oct1))
        assertNull(FundRules.checkExpenseDate(terms(), oct31))
        assertEquals(BusinessError.FundClosed, FundRules.checkExpenseDate(terms(status = FundStatus.CLOSED), LocalDate.of(2026, 10, 15)))
        assertEquals(BusinessError.DateOutOfFundRange, FundRules.checkExpenseDate(terms(), oct1.minusDays(1)))
        assertEquals(BusinessError.DateOutOfFundRange, FundRules.checkExpenseDate(terms(), oct31.plusDays(1)))
    }

    @Test
    fun aFundWithoutEndDateAcceptsLaterDates() {
        assertNull(FundRules.checkExpenseDate(terms(endsOn = null), LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun anExpenseBeforeTheLastCountIsRejectedButTheSameDayIsAccepted() {
        val counted = terms(lastCountOn = LocalDate.of(2026, 10, 10))

        assertEquals(BusinessError.DateBeforeLastCount, FundRules.checkExpenseDate(counted, LocalDate.of(2026, 10, 9)))
        assertNull(FundRules.checkExpenseDate(counted, LocalDate.of(2026, 10, 10)))
        assertNull(FundRules.checkExpenseDate(counted, LocalDate.of(2026, 10, 11)))
    }

    // ── Día de uso ──────────────────────────────────────────────────────────────────────────

    @Test
    fun aUsageDayIsOnlyForEstimateModeInsideTheRangeAndNeverInTheFuture() {
        val today = LocalDate.of(2026, 10, 20)

        assertNull(FundRules.checkUsageDay(terms(), LocalDate.of(2026, 10, 20), today))
        assertEquals(BusinessError.UsageDayNotAllowed, FundRules.checkUsageDay(terms(mode = ConsumptionMode.DETAILED), oct1, today))
        assertEquals(BusinessError.UsageDayNotAllowed, FundRules.checkUsageDay(terms(mode = ConsumptionMode.NONE), oct1, today))
        assertEquals(BusinessError.DateOutOfFundRange, FundRules.checkUsageDay(terms(), oct1.minusDays(1), today))
        assertEquals(BusinessError.DateOutOfFundRange, FundRules.checkUsageDay(terms(endsOn = LocalDate.of(2026, 10, 10)), LocalDate.of(2026, 10, 11), today))
        assertEquals(BusinessError.FutureDate, FundRules.checkUsageDay(terms(), LocalDate.of(2026, 10, 21), today))
        assertEquals(BusinessError.FundClosed, FundRules.checkUsageDay(terms(status = FundStatus.CLOSED), oct1, today))
    }

    // ── Bloqueos de edición ─────────────────────────────────────────────────────────────────

    @Test
    fun theModeIsLockedWithUsageDaysOrCountsAndTheStartWithExpensesOrUsageDays() {
        assertEquals(EditLocks(mode = false, startsOn = false), FundRules.editLocks(hasUsageDays = false, hasCounts = false, hasAssignedExpenses = false))
        assertEquals(EditLocks(mode = true, startsOn = true), FundRules.editLocks(hasUsageDays = true, hasCounts = false, hasAssignedExpenses = false))
        assertEquals(EditLocks(mode = true, startsOn = false), FundRules.editLocks(hasUsageDays = false, hasCounts = true, hasAssignedExpenses = false))
        assertEquals(EditLocks(mode = false, startsOn = true), FundRules.editLocks(hasUsageDays = false, hasCounts = false, hasAssignedExpenses = true))
    }

    @Test
    fun editingALockedFieldIsRejectedAndEditingAnUnlockedOrUnchangedOneIsFine() {
        val current = terms()
        val locked = EditLocks(mode = true, startsOn = true)

        assertEquals(BusinessError.FieldLocked, FundRules.checkEdit(current, FundEdit(ConsumptionMode.DETAILED, oct1), locked))
        assertEquals(BusinessError.FieldLocked, FundRules.checkEdit(current, FundEdit(ConsumptionMode.DAILY_ESTIMATE, oct1.plusDays(1)), locked))
        assertNull(FundRules.checkEdit(current, FundEdit(ConsumptionMode.DAILY_ESTIMATE, oct1), locked))
        assertNull(FundRules.checkEdit(current, FundEdit(ConsumptionMode.DETAILED, oct1.plusDays(1)), EditLocks(mode = false, startsOn = false)))
    }

    @Test
    fun aClosedFundCannotBeEdited() {
        assertEquals(BusinessError.FundClosed, FundRules.checkEdit(terms(status = FundStatus.CLOSED), FundEdit(ConsumptionMode.DAILY_ESTIMATE, oct1), EditLocks(false, false)))
    }

    @Test
    fun theEndCannotMoveBeforeTheLastActivityButCanMoveAfterOrBeAbsent() {
        val current = terms()
        val locks = EditLocks(mode = true, startsOn = true, latestActivityOn = oct1.plusDays(9))
        val edit = { ends: LocalDate? -> FundEdit(ConsumptionMode.DAILY_ESTIMATE, oct1, ends) }

        assertEquals(BusinessError.DateOutOfFundRange, FundRules.checkEdit(current, edit(oct1.plusDays(8)), locks))
        assertNull(FundRules.checkEdit(current, edit(oct1.plusDays(9)), locks))
        assertNull(FundRules.checkEdit(current, edit(oct1.plusDays(30)), locks))
        assertNull(FundRules.checkEdit(current, edit(null), locks))
        assertNull(FundRules.checkEdit(current, edit(oct1), EditLocks(mode = true, startsOn = true)))
    }
}
