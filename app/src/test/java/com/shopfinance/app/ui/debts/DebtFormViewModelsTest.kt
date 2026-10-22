package com.shopfinance.app.ui.debts

import com.shopfinance.app.data.debts.DebtInputRules
import com.shopfinance.app.data.debts.NewDebt
import com.shopfinance.app.data.debts.NewDebtor
import com.shopfinance.app.data.debts.NewDebtorError
import com.shopfinance.app.data.debts.NewPayment
import com.shopfinance.app.data.shop.ShopAccessException
import com.shopfinance.app.ui.fakes.FakeDebtRepository
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.debt
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.debtor
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.payment
import com.shopfinance.app.ui.fakes.FakeDebtRepository.Companion.snapshot
import com.shopfinance.app.ui.ledger.AmountError
import com.shopfinance.domain.debts.DebtStatus
import com.shopfinance.domain.debts.PaymentProblem
import com.shopfinance.domain.money.Currency
import com.shopfinance.domain.money.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * The forms run no coroutines themselves. The record-payment form is fed the shop's debts
 * by a [DebtsViewModel], as on screen, and that one needs a main dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebtFormViewModelsTest {

    private val today = LocalDate.of(2026, 10, 22)
    private lateinit var debts: FakeDebtRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        debts = FakeDebtRepository(today)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun usd(dollars: Long) = Money(dollars * 100, Currency.USD)

    private fun lbp(lira: Long) = Money(lira, Currency.LBP)

    private fun debtorForm() = DebtorFormViewModel(debts, shopId = "s1", createdBy = "alice")

    private fun debtForm() = DebtFormViewModel(debts, shopId = "s1", createdBy = "alice", debtorId = "r", today = { today })

    /** Rami owes $100 since 1 October 2026 (debt d1), with [paid] paid on 10 October. */
    private fun TestScope.paymentFormFor100Dollars(paid: Long = 0): PaymentFormViewModel {
        val payments = if (paid > 0) listOf(payment("p0", "d1", usd(paid), "2026-10-10")) else emptyList()
        debts.push("s1", snapshot(listOf(debtor("r", "رامي")), listOf(debt("d1", "r", usd(100), "2026-10-01")), payments))
        return paymentForm("d1")
    }

    /** A record-payment form fed the live debts, the way the screen feeds it from the debts list. */
    private fun TestScope.paymentForm(debtId: String): PaymentFormViewModel {
        val viewModel = PaymentFormViewModel(debts, shopId = "s1", createdBy = "alice", debtId = debtId, today = { today })
        val list = DebtsViewModel(debts, shopId = "s1", userUid = "alice", today = { today })
        backgroundScope.launch { list.state.collect { viewModel.onDebtsChange(it) } }
        runCurrent()
        return viewModel
    }

    /** The debtors list as it would show the shop's debts now, writes from the forms included. */
    private fun TestScope.listNow(): DebtsOverview {
        var overview: DebtsOverview? = null
        val viewModel = DebtsViewModel(debts, shopId = "s1", userUid = "alice", today = { today })
        backgroundScope.launch { viewModel.state.collect { overview = (it as? DebtsUiState.Ready)?.overview } }
        runCurrent()
        return overview!!
    }

    // The four scenarios asked for, through the record-payment form and the debtors list.

    @Test
    fun paying30Of100LeavesSeventyOpen() = runTest {
        val viewModel = paymentFormFor100Dollars()
        viewModel.onAmountChange("30")
        viewModel.submit()
        runCurrent()

        assertEquals(NewPayment(usd(30), today, ""), debts.paymentCalls.single().payment)
        assertTrue(viewModel.state.value.saved)
        val account = listNow().account("d1")!!
        assertEquals(usd(70), account.remaining)
        assertEquals(DebtStatus.OPEN, account.status)
        assertEquals(listOf("رامي"), listNow().rows(DebtorFilter.OPEN, "").map { it.debtor.name })
    }

    @Test
    fun payingTheLast70MarksItPaidAndKeepsItUnderPaid() = runTest {
        val viewModel = paymentFormFor100Dollars(paid = 30)
        viewModel.onAmountChange("70")
        viewModel.submit()
        runCurrent()

        assertTrue(viewModel.state.value.saved)
        val list = listNow()
        val account = list.account("d1")!!
        assertEquals(Money.zero(Currency.USD), account.remaining)
        assertEquals(DebtStatus.PAID, account.status)
        assertEquals(2, account.payments.size)
        assertTrue(list.rows(DebtorFilter.OPEN, "").isEmpty())
        assertEquals(listOf("رامي"), list.rows(DebtorFilter.PAID, "").map { it.debtor.name })
    }

    @Test
    fun paying80When70RemainsIsRefusedAndNothingIsSaved() = runTest {
        val viewModel = paymentFormFor100Dollars(paid = 30)
        viewModel.onAmountChange("80")
        viewModel.submit()
        runCurrent()

        val form = viewModel.state.value
        assertEquals(PaymentProblem.MORE_THAN_REMAINING, form.problem)
        assertFalse(form.saved)
        assertTrue(debts.paymentCalls.isEmpty())
        assertEquals(usd(70), listNow().account("d1")!!.remaining)

        // Typing again clears the error.
        viewModel.onAmountChange("70")
        assertNull(viewModel.state.value.problem)
    }

    @Test
    fun aPersonWithDollarAndLiraDebtsShowsTwoTotals() = runTest {
        debts.push("s1", snapshot(listOf(debtor("r", "رامي"))))
        val dollars = debtForm()
        dollars.onAmountChange("100")
        dollars.submit()
        val lira = debtForm()
        lira.onCurrencyChange(Currency.LBP)
        lira.onAmountChange("4500000")
        lira.submit()
        runCurrent()

        val list = listNow()
        assertEquals(listOf(usd(100), lbp(4_500_000)), list.row("r")!!.balance.remaining.toList())
        assertEquals(listOf(usd(100), lbp(4_500_000)), list.totalRemaining.toList())
    }

    // Record payment.

    @Test
    fun theFormFollowsItsDebtAndStartsOnToday() = runTest {
        val viewModel = paymentFormFor100Dollars(paid = 30)

        val form = viewModel.state.value
        assertFalse(form.loading)
        assertEquals(usd(70), form.account!!.remaining)
        assertEquals(today, form.date)
        assertEquals("", form.amountText)
    }

    @Test
    fun payInFullFillsInWhatIsStillOwed() = runTest {
        val viewModel = paymentFormFor100Dollars(paid = 30)
        viewModel.payInFull()
        assertEquals("70.00", viewModel.state.value.amountText)

        viewModel.submit()
        runCurrent()
        assertEquals(usd(70), debts.paymentCalls.single().payment.amount)
        assertEquals(DebtStatus.PAID, listNow().account("d1")!!.status)
    }

    @Test
    fun payInFullOfALiraDebtHasNoDecimals() = runTest {
        debts.push("s1", snapshot(listOf(debtor("r", "رامي")), listOf(debt("d1", "r", lbp(500_000), "2026-10-01"))))
        val viewModel = paymentForm("d1")
        viewModel.payInFull()
        assertEquals("500000", viewModel.state.value.amountText)

        viewModel.onAmountChange("1000.5")
        viewModel.submit()
        assertEquals(AmountError.TOO_MANY_DECIMALS, viewModel.state.value.amountError)
        assertTrue(debts.paymentCalls.isEmpty())
    }

    @Test
    fun zeroOrNegativeIsRefused() = runTest {
        val viewModel = paymentFormFor100Dollars()
        viewModel.onAmountChange("0")
        viewModel.submit()
        assertEquals(AmountError.ZERO, viewModel.state.value.amountError)

        viewModel.onAmountChange("-5")
        viewModel.submit()
        assertEquals(AmountError.NEGATIVE, viewModel.state.value.amountError)

        viewModel.onAmountChange("")
        viewModel.submit()
        assertEquals(AmountError.EMPTY, viewModel.state.value.amountError)
        assertTrue(debts.paymentCalls.isEmpty())
    }

    @Test
    fun anOlderPaymentDayCanBePickedButNotOneBeforeTheDebt() = runTest {
        val viewModel = paymentFormFor100Dollars()
        viewModel.onAmountChange("10")

        viewModel.onDateChange(today.plusDays(1))
        assertEquals(today, viewModel.state.value.date)

        viewModel.onDateChange(LocalDate.of(2026, 9, 30))
        viewModel.submit()
        assertEquals(PaymentProblem.BEFORE_DEBT_DATE, viewModel.state.value.problem)
        assertTrue(debts.paymentCalls.isEmpty())

        viewModel.onDateChange(LocalDate.of(2026, 10, 1))
        viewModel.submit()
        assertEquals(LocalDate.of(2026, 10, 1), debts.paymentCalls.single().payment.paymentDate)
    }

    @Test
    fun aPaidDebtTakesNoMorePayments() = runTest {
        val viewModel = paymentFormFor100Dollars(paid = 100)
        viewModel.payInFull()
        assertEquals("", viewModel.state.value.amountText)

        viewModel.onAmountChange("1")
        viewModel.submit()
        assertEquals(PaymentProblem.MORE_THAN_REMAINING, viewModel.state.value.problem)
        assertTrue(debts.paymentCalls.isEmpty())
    }

    @Test
    fun aDeletedDebtCannotBePaid() = runTest {
        debts.push("s1", snapshot(listOf(debtor("r", "رامي"))))
        val viewModel = paymentForm("d1")
        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.account)

        viewModel.onAmountChange("10")
        viewModel.submit()
        assertTrue(debts.paymentCalls.isEmpty())
    }

    @Test
    fun theFormWaitsForTheFirstRead() = runTest {
        val viewModel = paymentForm("d1")

        assertTrue(viewModel.state.value.loading)
        assertNull(viewModel.state.value.account)
    }

    @Test
    fun aFailedReadIsReportedNotTakenForADeletedDebt() = runTest {
        debts.fail("s1", ShopAccessException())
        val viewModel = paymentForm("d1")

        assertFalse(viewModel.state.value.loading)
        assertEquals(DebtsUiState.Error(accessDenied = true), viewModel.state.value.readError)
        viewModel.onAmountChange("10")
        viewModel.submit()
        assertTrue(debts.paymentCalls.isEmpty())
    }

    @Test
    fun aReadAfterAFailureClearsTheError() {
        val viewModel = PaymentFormViewModel(debts, shopId = "s1", createdBy = "alice", debtId = "d1", today = { today })
        viewModel.onDebtsChange(DebtsUiState.Error(accessDenied = false))
        val snapshot = snapshot(listOf(debtor("r", "رامي")), listOf(debt("d1", "r", usd(100), "2026-10-01")))
        viewModel.onDebtsChange(DebtsUiState.Ready(DebtsOverview(snapshot, today)))

        assertNull(viewModel.state.value.readError)
        assertEquals(usd(100), viewModel.state.value.account!!.remaining)
    }

    @Test
    fun aPaymentIsSavedOnce() = runTest {
        val viewModel = paymentFormFor100Dollars()
        viewModel.onAmountChange("10")
        viewModel.onNoteChange("نقداً")
        viewModel.submit()
        viewModel.submit()
        runCurrent()

        assertEquals(
            listOf(FakeDebtRepository.PaymentCall("s1", "alice", "d1", NewPayment(usd(10), today, "نقداً"))),
            debts.paymentCalls,
        )
    }

    // Add debtor.

    @Test
    fun aDebtorNeedsAName() {
        val viewModel = debtorForm()
        viewModel.onNameChange("   ")
        viewModel.submit()

        assertEquals(NewDebtorError.NAME_EMPTY, viewModel.state.value.nameError)
        assertTrue(debts.debtorCalls.isEmpty())
    }

    @Test
    fun aBadPhoneOrLongNoteIsRefused() {
        val viewModel = debtorForm()
        viewModel.onNameChange("رامي")
        viewModel.onPhoneChange("call me")
        viewModel.onNoteChange("س".repeat(DebtInputRules.NOTE_MAX_LENGTH + 1))
        viewModel.submit()

        assertTrue(viewModel.state.value.phoneInvalid)
        assertTrue(viewModel.state.value.noteTooLong)
        assertTrue(debts.debtorCalls.isEmpty())
    }

    @Test
    fun aDebtorIsSavedOnceAndTheirIdIsKept() {
        val viewModel = debtorForm()
        viewModel.onNameChange("رامي")
        viewModel.onPhoneChange("+961 3 123 456")
        viewModel.submit()
        viewModel.submit()

        assertEquals(
            listOf(FakeDebtRepository.DebtorCall("s1", "alice", NewDebtor("رامي", "+961 3 123 456", ""))),
            debts.debtorCalls,
        )
        assertEquals("debtor-1", viewModel.state.value.savedId)
    }

    @Test
    fun aRefusedDebtorIsReported() {
        debts.addError = IllegalArgumentException("refused")
        val viewModel = debtorForm()
        viewModel.onNameChange("رامي")
        viewModel.submit()

        assertTrue(viewModel.state.value.saveFailed)
        assertNull(viewModel.state.value.savedId)
    }

    // Add debt.

    @Test
    fun aDebtStartsInDollarsOnToday() {
        val form = debtForm().state.value
        assertEquals(Currency.USD, form.currency)
        assertEquals(today, form.date)
    }

    @Test
    fun anOldDebtKeepsTheDayTheMoneyWasTaken() {
        val viewModel = debtForm()
        viewModel.onAmountChange("100")
        viewModel.onDateChange(LocalDate.of(2025, 3, 15))
        viewModel.onNoteChange("بضاعة")
        viewModel.submit()

        assertEquals(
            listOf(FakeDebtRepository.DebtCall("s1", "alice", NewDebt("r", usd(100), LocalDate.of(2025, 3, 15), "بضاعة"))),
            debts.debtCalls,
        )
    }

    @Test
    fun aDebtDayCannotBeInTheFutureOrBefore2000() {
        val viewModel = debtForm()
        viewModel.onDateChange(today.plusDays(1))
        viewModel.onDateChange(LocalDate.of(1999, 12, 31))
        assertEquals(today, viewModel.state.value.date)

        viewModel.onDateChange(DebtInputRules.EARLIEST_DAY)
        assertEquals(DebtInputRules.EARLIEST_DAY, viewModel.state.value.date)
    }

    @Test
    fun liraAmountsAreWholeAndCapped() {
        val viewModel = debtForm()
        viewModel.onCurrencyChange(Currency.LBP)
        viewModel.onAmountChange("1500.5")
        viewModel.submit()
        assertEquals(AmountError.TOO_MANY_DECIMALS, viewModel.state.value.amountError)

        viewModel.onAmountChange((DebtInputRules.MAX_AMOUNT_MINOR + 1).toString())
        viewModel.submit()
        assertEquals(AmountError.TOO_LARGE, viewModel.state.value.amountError)
        assertTrue(debts.debtCalls.isEmpty())

        viewModel.onAmountChange(DebtInputRules.MAX_AMOUNT_MINOR.toString())
        viewModel.submit()
        assertEquals(lbp(DebtInputRules.MAX_AMOUNT_MINOR), debts.debtCalls.single().debt.amount)
    }

    @Test
    fun aDebtIsSavedOnce() {
        val viewModel = debtForm()
        viewModel.onAmountChange("12.50")
        viewModel.submit()
        viewModel.submit()
        viewModel.onAmountChange("99")

        assertEquals(1, debts.debtCalls.size)
        assertEquals(Money(1_250, Currency.USD), debts.debtCalls.single().debt.amount)
        assertEquals("12.50", viewModel.state.value.amountText)
    }
}
