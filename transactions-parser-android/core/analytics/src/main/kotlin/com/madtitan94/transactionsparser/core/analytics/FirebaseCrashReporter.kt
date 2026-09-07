package com.madtitan94.transactionsparser.core.analytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.madtitan94.transactionsparser.core.domain.analytics.CrashReporter

/**
 * Crashlytics behind the app's own interface.
 *
 * Uncaught crashes need nothing from this class — Crashlytics installs its own handler at startup
 * and reports them whether anything here is called or not. What this adds is the context that makes
 * a stack trace answerable: which account it happened to, and the breadcrumbs leading up to it.
 *
 * Nothing here may carry statement contents. A breadcrumb reading "parsed 40 rows" is useful; one
 * naming a payee puts somebody's spending in a crash report.
 */
class FirebaseCrashReporter(
    private val crashlytics: FirebaseCrashlytics,
    isEnabled: Boolean
) : CrashReporter {

    init {
        crashlytics.isCrashlyticsCollectionEnabled = isEnabled
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun recordNonFatal(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    override fun setIdentity(accountHash: String?) {
        // Crashlytics has no "clear the user id" call; the empty string is its documented way of
        // saying nobody, and it is safe here in a way it is not in analytics — a crash report is
        // read one at a time rather than grouped into a dimension.
        crashlytics.setUserId(accountHash.orEmpty())
    }
}
