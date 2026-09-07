package com.madtitan94.transactionsparser.core.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * What the user typed, resolved into the two things a search can actually match on.
 *
 * Parsing lives here rather than in SQL because it is the part with judgement in it: deciding that
 * `1250` means "any charge of ₹1,250" but also still means "a reference containing 1250" is a
 * product decision, and it is worth being able to test it without a database.
 *
 * A query is never *only* an amount. `1250` is a plausible amount and a plausible fragment of a
 * UTR, and a user who typed it cannot be assumed to have meant one or the other, so both matches
 * are offered and the row is returned if either fires.
 */
data class SearchQuery(
    /** The raw text, trimmed. Matched as a substring against names, aliases, UTRs and refs. */
    val text: String,
    /**
     * Inclusive paise bounds when the text reads as an amount, null when it does not.
     *
     * A range rather than one value because of how people type money. `1250` with no decimal part
     * is a rupee figure and the user means the rupee, not the exact paise — a card charge of
     * ₹1,250.37 is the one they are looking for. Typing the decimals narrows it back to the exact
     * paise, which is what someone copying a figure off a statement wants.
     */
    val amountFromPaise: Long?,
    val amountToPaise: Long?
) {
    /** Nothing to run: too short to be worth a table scan, and too broad to be worth reading. */
    val isBlank: Boolean get() = text.length < MIN_LENGTH

    companion object {
        /**
         * Below this the result set is the account, which is not a search result — it is the
         * export. Two rather than three because Indian statement names are full of two-letter
         * tokens the user reasonably searches by, and because at this scale the cost of a broad
         * match is a scan of a few thousand rows, not a slow screen.
         */
        const val MIN_LENGTH = 2

        /** Characters `LIKE` treats as wildcards, neutralised so a typed `%` matches a literal one. */
        private const val LIKE_ESCAPE = '\\'

        /**
         * Reads [raw] as a query.
         *
         * Currency decoration is stripped before the amount test so that `₹1,250` and `1250` are the
         * same search — the user is copying off a statement, not writing a number literal. The
         * stripping is deliberately *not* applied to [text]: a payee genuinely called `M&S` should
         * still be findable, so the text half keeps every character the user typed.
         */
        fun parse(raw: String): SearchQuery {
            val text = raw.trim()
            val amount = parseAmountPaise(text)
            return SearchQuery(
                text = text,
                amountFromPaise = amount?.first,
                amountToPaise = amount?.second
            )
        }

        /**
         * The inclusive paise window [text] describes as money, or null when it is not money.
         *
         * `BigDecimal` rather than `Double` because a rupee figure scaled by 100 is exactly the
         * kind of arithmetic binary floating point rounds wrongly, and this window decides whether
         * a row the user is looking at is returned at all.
         */
        private fun parseAmountPaise(text: String): Pair<Long, Long>? {
            val cleaned = text.replace(",", "").removePrefix("₹").removePrefix("Rs.")
                .removePrefix("Rs").trim()
            if (cleaned.isEmpty() || cleaned.any { !it.isDigit() && it != '.' }) return null
            val decimal = cleaned.toBigDecimalOrNull() ?: return null
            if (decimal.signum() < 0) return null

            val hasFraction = cleaned.contains('.')
            return if (hasFraction) {
                val paise = decimal.multiply(BigDecimal(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .toLongOrNull() ?: return null
                paise to paise
            } else {
                val rupees = decimal.toLongOrNull() ?: return null
                val from = rupees * 100
                from to (from + 99)
            }
        }

        /**
         * The exact `Long`, or null when the value does not fit or is not whole.
         *
         * Null rather than a truncation: a figure too large to be a real transaction should return
         * no amount match at all, not the wrapped-around number it happens to truncate to.
         */
        private fun BigDecimal.toLongOrNull(): Long? = try {
            longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * [text] as a `LIKE` pattern, with the wildcard characters escaped.
     *
     * Escaping matters more than it looks: `%` typed into the box would otherwise match every row
     * in the account and present it as a result, and `_` would silently match one character of
     * anything. The queries that bind this declare `ESCAPE '\'` to match.
     */
    fun likePattern(): String {
        val escaped = buildString {
            text.forEach { char ->
                if (char == '%' || char == '_' || char == LIKE_ESCAPE) append(LIKE_ESCAPE)
                append(char)
            }
        }
        return "%$escaped%"
    }
}

/**
 * One row of the search results, with everything the list draws already resolved.
 *
 * Resolved in SQL rather than by the screen because the payee's alias and its category come from
 * two more tables, and doing it per row on the main thread is how a search that is fast becomes a
 * search that feels slow.
 *
 * Excluded rows are deliberately searchable. The list is a finding tool over what the account
 * holds, not an aggregate — a user hunting for a specific charge is often hunting for exactly the
 * one they told the app to ignore — so [isExcluded] rides along as a flag the row is dimmed by
 * rather than as a filter that hides it.
 */
data class TransactionSearchResult(
    val id: Long,
    val dateTimeUtcMillis: Long,
    /** The user's alias when the payee is mapped, the statement's own name when it is not. */
    val label: String,
    /** The statement's own name, kept even when [label] is an alias, so a row can be opened. */
    val statementName: String,
    /** The key `PayeeDetailRoute` opens on — enough to reach the payee's merged history. */
    val normalizedName: String,
    val categoryName: String?,
    val amountPaise: Long,
    val type: TransactionType,
    val isExcluded: Boolean,
    val isDuplicate: Boolean
)

/**
 * A page of search results together with how many there really were.
 *
 * The count is the reason this is a wrapper rather than a bare list. The rows are fetched with a
 * limit, so counting them reports the limit — an account with four matches and one with four
 * hundred would both read as [SEARCH_RESULT_LIMIT], and the user would have no way to know their
 * search was too broad. [totalCount] is a `COUNT(*)` over the same predicate, so the screen can say
 * how much it is not showing.
 */
data class TransactionSearchPage(
    val results: List<TransactionSearchResult>,
    val totalCount: Int
) {
    /** More matched than were fetched — the screen says so rather than pretending it is complete. */
    val isTruncated: Boolean get() = totalCount > results.size

    companion object {
        val Empty = TransactionSearchPage(results = emptyList(), totalCount = 0)
    }
}

/** How many rows a search fetches before it stops and asks the user to narrow it down. */
const val SEARCH_RESULT_LIMIT = 200

/**
 * One charge that is out of character for the category it fell in.
 *
 * The comparison is per *transaction* against the average *transaction* in the same category, which
 * is a deliberate reading of the requirement rather than a literal one. Comparing a single charge
 * against a category's trailing three-month *total* is not a comparison at all — a ₹2,000 dinner
 * against a ₹18,000 quarter of eating out says nothing about whether the dinner was unusual. The
 * average charge is the only baseline a single charge can be measured against and produce a claim
 * the user can check against their own memory.
 */
data class SpendAnomaly(
    val transactionId: Long,
    val dateTimeUtcMillis: Long,
    /** The user's alias when mapped, the statement's own name when not. */
    val label: String,
    val statementName: String,
    val normalizedName: String,
    /** Null for the unmapped bucket, which has anomalies like any other category. */
    val categoryId: Long?,
    val categoryName: String?,
    val amountPaise: Long,
    /** The mean charge in this category over the baseline window. */
    val baselineMeanPaise: Long,
    /** How many charges that mean was taken from — the reason to believe it. */
    val baselineSampleCount: Int
) {
    /**
     * How many times the usual charge this one was, for the sentence the callout prints.
     *
     * Zero rather than an infinity when the baseline is zero. That state cannot occur while the
     * query enforces a minimum sample count, but a model that divides by a value it does not own
     * should not be the thing that crashes if that ever changes.
     */
    val multiple: Double
        get() = if (baselineMeanPaise <= 0L) 0.0 else amountPaise.toDouble() / baselineMeanPaise
}

/**
 * Which categories an anomaly read looks in.
 *
 * A sealed type rather than a nullable category id, because a null id is already taken: everywhere
 * else in this app it names the unmapped bucket — a real category with real spend in it — and
 * reusing it for "all of them" would make that bucket the one place anomalies could never be found.
 */
sealed interface AnomalyScope {
    /** Every category, for the dashboard, where the callout is about the account as a whole. */
    data object All : AnomalyScope

    /** One category, for its insight screen. [categoryId] null is the unmapped bucket. */
    data class OneCategory(val categoryId: Long?) : AnomalyScope
}

/**
 * The thresholds the anomaly query is run with, kept together because they only make sense together.
 *
 * These are a starting position, not a tuned one, and the issue's own survey note says as much: the
 * multiplier wants a few real months behind it before it can be called right. They are gathered
 * here so that tuning is one edit against one set of stated reasons rather than a hunt through SQL.
 */
object AnomalyThresholds {
    /**
     * Whole months of history the baseline is taken from, ending where the viewed period begins.
     *
     * Three, per the requirement. The window deliberately stops short of the period being examined:
     * including it would let an unusual charge raise the average it is being measured against, and
     * a large enough one would hide itself.
     */
    const val BASELINE_MONTHS = 3L

    /**
     * Charges the baseline needs before it is allowed to make a claim.
     *
     * Five, because a mean of two charges is not a habit and a category with one prior charge would
     * flag its second one every time. This is what makes a freshly imported account quiet rather
     * than full of callouts about a history it does not have yet.
     */
    const val MIN_SAMPLE_COUNT = 5

    /**
     * How many times the usual charge a charge has to be before it is worth interrupting for.
     *
     * Three. Untuned, and the one number here most likely to move: too low and the callout fires on
     * every slightly larger grocery run and trains the user to ignore it, too high and it only ever
     * reports things they had already noticed.
     */
    const val MULTIPLIER = 3.0

    /**
     * The floor below which a multiple is arithmetic rather than news.
     *
     * ₹500. A ₹40 coffee against a ₹12 usual is four times the average and is not worth a card on
     * the dashboard; the ratio is real and the money is not. Without this the callout fills up with
     * the smallest transactions in the account, which are also the ones with the noisiest averages.
     */
    const val MIN_AMOUNT_PAISE = 50_000L

    /**
     * How many callouts a screen will show at once.
     *
     * Three, because the callout competes with the dashboard it sits above. A month that produced
     * twenty unusual charges is a month the user should look at in the list, not a screen of
     * twenty banners.
     */
    const val LIMIT = 3

    /**
     * How many are fetched to fill the [LIMIT] that are shown.
     *
     * More than are shown, because the limit is applied in SQL while dismissals are applied
     * afterwards, in the ViewModel, against a preference the query cannot see. Fetching exactly
     * three and dismissing the top one would leave two callouts on a period that has a fourth
     * waiting — the banner would look like it was running out rather than being cleared.
     */
    const val FETCH_LIMIT = 12
}
