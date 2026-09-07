package com.madtitan94.transactionsparser.core.domain.analytics

/**
 * Every event this app is allowed to send, as a closed set rather than as free strings.
 *
 * A `track(name, params)` signature makes a typo in an event name a silent, permanent hole in the
 * data — the event goes to Firebase, Firebase accepts it, and nobody finds out until someone builds
 * a funnel on a name that was only ever sent once. A sealed type spends one class per event to make
 * that unrepresentable, and to put every event's parameter shape in one readable list.
 *
 * The runtime validation in [AnalyticsContract] still exists, because the *default* parameters are
 * assembled at call time from a live session and cannot be checked here.
 *
 * ### What must never appear in this file
 *
 * No payee names, no amounts, no file names, no statement contents. This is a public repository and
 * an app that reads bank statements; the events below carry counts, enum names and booleans, which
 * are enough to answer "does the import funnel finish?" without a single row of anybody's money
 * reaching Google. A parameter that would let someone reconstruct a transaction does not belong in
 * an analytics event, however useful it would be.
 */
sealed interface AnalyticsEvent {

    /** Firebase event name. Lower snake case, ≤ 40 chars — enforced by [AnalyticsContract]. */
    val name: String

    /** Event-specific parameters, merged with the default ones at send time. */
    val params: Map<String, Any> get() = emptyMap()

    /**
     * A cold start of the app.
     *
     * Distinct from Firebase's automatic `app_open`, which also counts a return from the background.
     * This one fires once per process, so "sessions" and "launches" stay separable.
     */
    data object AppStarted : AnalyticsEvent {
        override val name = "app_started"
    }

    /**
     * A statement PDF was run through the parser.
     *
     * [source] is the issuer enum name — `PHONEPE` or `GOOGLE_PAY` — never the file name, which on
     * a phone routinely contains the account holder's own name. [transactionCount] is zero on a
     * failure, which is what makes [succeeded] worth carrying separately: a parse that succeeds with
     * nothing in it and a parse that threw are different problems.
     */
    data class StatementImported(
        val source: String,
        val transactionCount: Int,
        val succeeded: Boolean,
        /**
         * The `ParseError` name when the import failed, absent when it did not.
         *
         * Beyond what a bare "a PDF was uploaded" event needs, and the single most useful thing
         * this app can learn: an import that fails is a user who gets nothing, and the reason
         * separates a bank format nobody has written a parser for from a file that was never a
         * statement. An enum name, so it cannot carry anything out of the document.
         */
        val failureReason: String? = null
    ) : AnalyticsEvent {
        override val name = "statement_imported"
        override val params = buildMap {
            put(AnalyticsParams.SOURCE, source)
            put(AnalyticsParams.TRANSACTION_COUNT, transactionCount)
            put(AnalyticsParams.SUCCEEDED, succeeded)
            failureReason?.let { put(AnalyticsParams.FAILURE_REASON, it) }
        }
    }

    /**
     * Every payee in a statement now has a category.
     *
     * The half of the funnel that says whether importing a statement was worth anything: an import
     * whose payees are never mapped produces no dashboard, so a high [StatementImported] count with
     * a low completion count is the failure worth seeing.
     */
    data class MappingCompleted(
        val payeeCount: Int,
        val transactionCount: Int
    ) : AnalyticsEvent {
        override val name = "mapping_completed"
        override val params = mapOf(
            AnalyticsParams.PAYEE_COUNT to payeeCount,
            AnalyticsParams.TRANSACTION_COUNT to transactionCount
        )
    }

    /**
     * A statement session was cancelled before its payees were all mapped.
     *
     * [mappedCount] against [unmappedCount] says *where* people give up — abandoning at zero mapped
     * is a different product problem from abandoning at forty of fifty.
     *
     * Both are counts of transactions rather than of payees, because that is what the history
     * screen already knows when the session is cancelled from it, and because it is the number the
     * user is looking at on that row.
     */
    data class MappingCancelled(
        val mappedCount: Int,
        val unmappedCount: Int
    ) : AnalyticsEvent {
        override val name = "mapping_cancelled"
        override val params = mapOf(
            AnalyticsParams.MAPPED_COUNT to mappedCount,
            AnalyticsParams.UNMAPPED_COUNT to unmappedCount
        )
    }

    /**
     * The dashboard Home opens on was changed.
     *
     * [dashboard] is the built-in dashboard's enum name, or the literal `CUSTOM` for a user-built
     * one. Deliberately not the stored id: a custom dashboard's id is `custom:<uuid>`, which is
     * unique per user and would turn this into a per-user dimension that answers nothing.
     */
    data class DefaultDashboardChanged(val dashboard: String) : AnalyticsEvent {
        override val name = "default_dashboard_changed"
        override val params = mapOf(AnalyticsParams.DASHBOARD to dashboard)
    }

    /**
     * Data left the app through an export.
     *
     * [format] separates the CSV export from the backup file, which are different features that
     * happen to share a screen.
     */
    data class DataExported(
        val format: String,
        val rowCount: Int
    ) : AnalyticsEvent {
        override val name = "data_exported"
        override val params = mapOf(
            AnalyticsParams.FORMAT to format,
            AnalyticsParams.ROW_COUNT to rowCount
        )
    }

    /** A Google sign-in completed. Carries nothing: who signed in is the default parameters' job. */
    data object LoggedIn : AnalyticsEvent {
        override val name = "logged_in"
    }

    /**
     * The user signed out.
     *
     * Sent *before* the session is cleared, so it still carries the account hash it belongs to.
     * Afterwards there is no identity to attribute it to and the event lands anonymous.
     */
    data object LoggedOut : AnalyticsEvent {
        override val name = "logged_out"
    }
}

/** Parameter keys, in one place so a rename cannot half-apply and split a dimension in two. */
object AnalyticsParams {
    // Identity and build, supplied by the default parameter provider on every event.
    const val ACCOUNT_HASH = "accountHash"
    const val EMAIL_HASH = "emailHash"
    const val APP_NAME = "appName"
    const val APP_VERSION = "appVersion"
    const val DEVICE = "device"
    const val MANUFACTURER = "manufacturer"
    const val MODEL = "model"
    const val OS_VERSION = "osVersion"
    const val OS_RELEASE = "osRelease"

    // Event-specific.
    const val SOURCE = "source"
    const val TRANSACTION_COUNT = "transactionCount"
    const val SUCCEEDED = "succeeded"
    const val FAILURE_REASON = "failureReason"
    const val PAYEE_COUNT = "payeeCount"
    const val MAPPED_COUNT = "mappedCount"
    const val UNMAPPED_COUNT = "unmappedCount"
    const val DASHBOARD = "dashboard"
    const val FORMAT = "format"
    const val ROW_COUNT = "rowCount"

    /**
     * [SOURCE] when the file never got far enough to be attributed to an issuer — it was not a PDF,
     * or no parser recognised it. A named bucket rather than an absent key, because "we could not
     * tell what this was" is itself the answer worth counting.
     */
    const val SOURCE_UNKNOWN = "UNKNOWN"

    /** Values for [FORMAT]. */
    const val FORMAT_CSV = "csv"
    const val FORMAT_BACKUP = "backup"

    /** Value for [AnalyticsEvent.DefaultDashboardChanged.dashboard] when the choice is user-built. */
    const val DASHBOARD_CUSTOM = "CUSTOM"
}
