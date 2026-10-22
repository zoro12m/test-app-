package com.shopfinance.app.ui.debts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shopfinance.app.R
import com.shopfinance.app.data.debts.DebtRejection
import com.shopfinance.domain.debts.DebtStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A calendar day as the debt screens show it, e.g. "1 Oct 2026" in the phone's language. */
@Composable
internal fun dayText(day: LocalDate): String {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    return formatter.format(day)
}

/** "today", "yesterday", "5 days ago", with the language's plural forms. */
@Composable
internal fun daysAgoText(days: Long): String = when (days) {
    0L -> stringResource(R.string.days_ago_today)
    1L -> stringResource(R.string.days_ago_yesterday)
    else -> {
        val count = days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        pluralStringResource(R.plurals.days_ago, count, count)
    }
}

@Composable
internal fun debtStatusLabel(status: DebtStatus): String = when (status) {
    DebtStatus.OPEN -> stringResource(R.string.debt_status_open)
    DebtStatus.PAID -> stringResource(R.string.debt_status_paid)
}

@Composable
internal fun filterLabel(filter: DebtorFilter): String = when (filter) {
    DebtorFilter.OPEN -> stringResource(R.string.debts_filter_open)
    DebtorFilter.PAID -> stringResource(R.string.debts_filter_paid)
    DebtorFilter.ALL -> stringResource(R.string.debts_filter_all)
}

@Composable
internal fun debtRejectionMessage(rejection: DebtRejection): String = when {
    rejection.accessDenied -> stringResource(R.string.rejection_access_denied)
    rejection.kind == DebtRejection.Kind.DELETE_DEBT || rejection.kind == DebtRejection.Kind.DELETE_PAYMENT ->
        stringResource(R.string.debts_rejection_delete)
    else -> stringResource(R.string.debts_rejection_add)
}
