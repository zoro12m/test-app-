package com.shopfinance.app.ui.ledger

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shopfinance.app.R
import com.shopfinance.app.data.ledger.ExpenseCategory
import com.shopfinance.app.data.ledger.LedgerInputRules
import com.shopfinance.app.data.ledger.LedgerRejection
import com.shopfinance.app.data.ledger.VoidReasonError
import com.shopfinance.domain.ledger.EntryType
import com.shopfinance.domain.money.Currency

/** What an entry of [type] is called in lists. */
@Composable
internal fun entryTypeLabel(type: EntryType): String = when (type) {
    EntryType.SALE -> stringResource(R.string.entry_type_sale)
    EntryType.EXPENSE -> stringResource(R.string.entry_type_expense)
    EntryType.CAPITAL_IN -> stringResource(R.string.entry_type_capital_in)
    EntryType.CAPITAL_OUT -> stringResource(R.string.entry_type_capital_out)
    // Not recorded by the app yet; shown by their stored name if one ever appears.
    else -> type.wireName
}

/** The title of the form that records an entry of [type]. */
@Composable
internal fun newEntryTitle(type: EntryType): String = when (type) {
    EntryType.SALE -> stringResource(R.string.entry_new_title_sale)
    EntryType.EXPENSE -> stringResource(R.string.entry_new_title_expense)
    EntryType.CAPITAL_IN -> stringResource(R.string.entry_new_title_capital_in)
    EntryType.CAPITAL_OUT -> stringResource(R.string.entry_new_title_capital_out)
    else -> type.wireName
}

/** A line explaining entries of [type], for the types whose meaning is not obvious. */
@Composable
internal fun newEntryHint(type: EntryType): String? = when (type) {
    EntryType.CAPITAL_IN -> stringResource(R.string.entry_new_hint_capital_in)
    EntryType.CAPITAL_OUT -> stringResource(R.string.entry_new_hint_capital_out)
    else -> null
}

@Composable
internal fun expenseCategoryLabel(category: ExpenseCategory): String = when (category) {
    ExpenseCategory.GOODS -> stringResource(R.string.category_goods)
    ExpenseCategory.RENT -> stringResource(R.string.category_rent)
    ExpenseCategory.ELECTRICITY -> stringResource(R.string.category_electricity)
    ExpenseCategory.SALARIES -> stringResource(R.string.category_salaries)
    ExpenseCategory.TRANSPORT -> stringResource(R.string.category_transport)
    ExpenseCategory.MAINTENANCE -> stringResource(R.string.category_maintenance)
    ExpenseCategory.OTHER -> stringResource(R.string.category_other)
}

/** Why the typed amount is refused. Lira have no decimals, so their decimal error says that. */
@Composable
internal fun amountErrorMessage(error: AmountError, currency: Currency = Currency.USD): String = when (error) {
    AmountError.EMPTY -> stringResource(R.string.amount_error_empty)
    AmountError.INVALID -> stringResource(R.string.amount_error_invalid)
    AmountError.NEGATIVE -> stringResource(R.string.amount_error_negative)
    AmountError.TOO_MANY_DECIMALS -> stringResource(
        if (currency.minorDigits == 0) R.string.amount_error_lbp_decimals else R.string.amount_error_too_many_decimals,
    )
    AmountError.ZERO -> stringResource(R.string.amount_error_zero)
    AmountError.TOO_LARGE -> stringResource(R.string.amount_error_too_large)
}

@Composable
internal fun voidReasonErrorMessage(error: VoidReasonError): String = when (error) {
    VoidReasonError.EMPTY -> stringResource(R.string.void_reason_empty)
    VoidReasonError.TOO_LONG -> stringResource(R.string.void_reason_too_long, LedgerInputRules.VOID_REASON_MAX_LENGTH)
}

@Composable
internal fun rejectionMessage(rejection: LedgerRejection): String = when {
    rejection.accessDenied -> stringResource(R.string.rejection_access_denied)
    rejection.kind == LedgerRejection.Kind.ADD -> stringResource(R.string.rejection_add)
    else -> stringResource(R.string.rejection_void)
}
