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
    /** The statement's own name, kept even when [label] is an alias, so a row can be opened. */
    val statementName: String,
    /** One of the payee's normalised names — enough to open its history, merged siblings and all. */
    val normalizedName: String,
    val totalPaise: Long,
    val transactionCount: Int
) {
    /** No payee behind the name yet. These rank all the same; unmapped money is money. */
    val isUnmapped: Boolean get() = payeeId == null
}

/**
 * How many payees a range's spend went to, and how much of it went to nobody in particular.
 *
 * The counts are the reason this exists. A ranked list can only answer "who were the biggest", and
 * it is fetched with a limit, so counting its rows reports the limit rather than the account: an
 * account with two hundred payees and one with forty both read as forty. These come from a
 * `COUNT(DISTINCT …)` over the whole range instead, so the figures on screen are the account's.
 *
 * [unmappedPaise] duplicates the null bucket of [CategoryTotal] on purpose — it is the same money
 * seen from the other side, and having it here means the nudge does not have to reach into a list of
 * category totals and pick out the one row that is not a category.
 *
 * [unmappedPayeeCount] is what turns a percentage into a task: a share of spend is a score, while a
 * count of names is an afternoon's work the user can picture finishing.
 */
data class PayeeSummary(
    /** Distinct payees with spend in the range, merged identities counted once. */
    val payeeCount: Int = 0,
    val unmappedPaise: Long = 0L,
    val unmappedPayeeCount: Int = 0,
    val unmappedTransactionCount: Int = 0
)

/**
 * What one category spent over a range, against what the whole account spent over the same one.
 *
 * The pair is read together rather than assembled from two flows so that [sharePercent] is taken
 * from a single moment. A share stitched from two independently collected totals can briefly
 * describe a state the database was never in, and the header would then contradict the dashboard
 * the user tapped through from.
 *
 * Debits only, throughout — this answers "where did it go", and a salary landing is not spend under
 * a category.
 */
data class CategoryShare(
    val totalPaise: Long,
    val transactionCount: Int,
    /** Distinct payees this category's spend reached in the range, merged identities counted once. */
    val payeeCount: Int,
    /** Every countable debit of the account over the same range, this category's included. */
    val accountTotalPaise: Long
) {
    /**
     * This category's share of the account's spend, 0-100, or null when there is nothing to divide.
     *
     * Null rather than zero for an empty account: "0% of all spend" is a claim about a period that
     * had spending, and a month with nothing in it has not earned it.
     */
    val sharePercent: Int?
        get() = if (accountTotalPaise <= 0L) {
            null
        } else {
            ((totalPaise * 100.0) / accountTotalPaise).toInt()
        }
}

/**
 * One row of the payee directory — everyone the account knows about, over all time.
 *
 * Unlike [PayeeTotal] this is not ranked spend for a period; it is the roster. Two things follow
 * from that and are the reason it is a separate model rather than a reused one:
 *
 * - **A payee with no countable spend still has a row**, at zero. [PayeeTotal] is built by grouping
 *   transactions, so a payee whose every transaction is excluded simply has nothing to group and
 *   disappears — correct for a ranked chart, wrong for a directory the user came to in order to
 *   *find* that payee and fix it.
 * - **Unclaimed statement names are listed too**, as rows with no [payeeId]. A directory of only the
 *   named payees would present a partial list as a complete one and would disagree with
 *   [PayeeSummary.payeeCount] about what the account contains.
 *
 * [identifierCount] is what lets the screen say "also known as 2 other names" without a second
 * query per row. It is zero, not one, for an unclaimed name: the point is not that the payee has no
 * names but that no identifier has ever claimed this one.
 */
data class PayeeDirectoryEntry(
    val payeeId: Long?,
    /** The user's alias when mapped, the statement's own name when not. */
    val label: String,
    /** The statement's own name, kept even when [label] is an alias, so a row can be opened. */
    val statementName: String,
    /** One of the payee's normalised names — enough to open its history, merged siblings and all. */
    val normalizedName: String,
    /** Null exactly when [payeeId] is; an unclaimed name has no category to show. */
    val categoryName: String?,
    val identifierCount: Int,
    val totalPaise: Long,
    val transactionCount: Int
) {
    /** No payee behind the name yet — the rows the directory's Unmapped filter is for. */
    val isUnmapped: Boolean get() = payeeId == null
}
