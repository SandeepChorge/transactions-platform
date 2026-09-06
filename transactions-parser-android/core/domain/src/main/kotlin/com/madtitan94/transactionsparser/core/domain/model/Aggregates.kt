package com.madtitan94.transactionsparser.core.domain.model

/**
 * The window a dashboard is asking about, as a half-open interval `[fromMillis, toMillisExclusive)`.
 *
 * Half-open rather than inclusive on both ends so a row at midnight belongs to exactly one of two
 * adjacent ranges. An inclusive upper bound would count the first instant of the next month twice —
 * once in each — which is the kind of error that shows up as a total that is off by one transaction
 * and cannot be explained from the screen.
 *
 * The bounds are in the same clock the rows are stored in: the statement's printed wall time read
 * back as UTC. Whoever builds a range for "this month" must therefore do the calendar arithmetic
 * with [java.time.ZoneOffset.UTC] and not with the device zone — a local-zone boundary would move
 * rows into the neighbouring month for every user who is not on UTC.
 */
data class DateRange(
    val fromMillis: Long,
    val toMillisExclusive: Long
) {
    companion object {
        /** Everything the account holds — the "All time" filter, and the safe default. */
        val AllTime = DateRange(Long.MIN_VALUE, Long.MAX_VALUE)
    }
}

/**
 * One day of the account's activity, both directions kept apart.
 *
 * Split rather than netted because the two answer different questions and a net figure cannot be
 * un-netted afterwards: the trend chart plots what was spent, while the day a salary lands would
 * otherwise show up as a day of negative spending.
 */
data class DayTotal(
    /** Midnight UTC of the day, matching how the rows themselves are bucketed. */
    val startMillis: Long,
    val debitPaise: Long,
    val creditPaise: Long,
    val transactionCount: Int
)

/**
 * Money in, money out and the difference, over one range — the three KPI tiles.
 *
 * [netPaise] is derived rather than stored so it cannot drift from the two figures beside it.
 */
data class TypeTotals(
    val debitPaise: Long = 0L,
    val creditPaise: Long = 0L,
    val debitCount: Int = 0,
    val creditCount: Int = 0
) {
    val netPaise: Long get() = creditPaise - debitPaise
}

/**
 * Spend under one category over a range.
 *
 * [categoryId] is null for the one bucket that is not a category at all: rows whose payee is not
 * mapped yet. Every mapped payee carries a category — `payees.categoryId` is non-null — so an
 * unmapped payee is the *only* way spend can arrive without one. That bucket is what the charts
 * draw as the hatch, and what the Mapping health dashboard reports as the unmapped share; dropping
 * it would present a partial total as a complete one.
 */
data class CategoryTotal(
    val categoryId: Long?,
    /** Null exactly when [categoryId] is — the "Uncategorised" label belongs to the UI layer. */
    val categoryName: String?,
    val totalPaise: Long,
    val transactionCount: Int
)

/**
 * Spend to one payee over a range, ranked.
 *
 * A payee here is the *merged* payee: every statement name mapped onto it totals into this one row.
 * Grouping on the statement name instead would split `SWIGGY`, `SWIGGY BANGALORE` and
 * `SWIGGY*ORDER` into three under-counted rows and could push the user's actual largest payee out
 * of the top five.
 *
 * [payeeId] is null when the row's statement name resolves to no payee at all. Those still rank —
 * unmapped money is money — under the name the statement printed.
 */
data class PayeeTotal(
    val payeeId: Long?,
    /** The user's alias when mapped, the statement's own name when not. */
    val label: String,
    val totalPaise: Long,
    val transactionCount: Int
)
