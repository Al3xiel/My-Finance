package com.al3xiel.my_finances.feature.loans.ui.components

import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LoanStatusLabelsTest {
    @Test fun anOpenLoanIsOpenUntilSomethingCameBackThenPartial() {
        assertEquals(R.string.loan_detail_status_open, loanStatusLabelRes(LoanStatus.OPEN, partlyRepaid = false))
        assertEquals(R.string.status_partial, loanStatusLabelRes(LoanStatus.OPEN, partlyRepaid = true))
    }

    @Test fun aRepaidLoanIsRepaidWhateverWasPaidBefore() {
        assertEquals(R.string.loan_detail_status_repaid, loanStatusLabelRes(LoanStatus.REPAID, partlyRepaid = false))
        assertEquals(R.string.loan_detail_status_repaid, loanStatusLabelRes(LoanStatus.REPAID, partlyRepaid = true))
    }

    @Test fun openWaitsAndRepaidIsDone() {
        assertEquals(BadgeStatus.PENDING, LoanStatus.OPEN.badge())
        assertEquals(BadgeStatus.RECONCILED, LoanStatus.REPAID.badge())
    }
}
