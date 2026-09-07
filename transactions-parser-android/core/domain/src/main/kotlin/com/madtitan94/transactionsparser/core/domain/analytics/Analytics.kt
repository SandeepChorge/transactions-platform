package com.madtitan94.transactionsparser.core.domain.analytics

import kotlinx.coroutines.flow.Flow

/**
 * The one way anything in this app reports that something happened.
 *
 * Deliberately fire-and-forget and never suspending: a ViewModel handling a tap must not be able to
 * make the tap slower, or fail, because of analytics. Everything expensive — the readiness gate,
 * the identity lookup, the actual send — happens behind this call.
 */
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
}

/**
 * The identity and build parameters attached to every event.
 *
 * Called on each send rather than captured once, because the identity changes underneath it: a user
 * signs in, and the events after that point must carry the account the events before it could not.
 * Implementations are expected to be cheap and non-blocking — the app's reads from a cached session
 * value, never from disk.
 *
 * Returning a map without the identity keys is the correct representation of "nobody is signed in".
 * See [AnalyticsIdentity.hash] for why an empty string would be worse than an absent key.
 */
fun interface DefaultAnalyticsParameterProvider {
    fun defaultParams(): Map<String, Any>
}

/**
 * Whether the default parameters can be trusted yet.
 *
 * Session state is read from disk asynchronously, so for the first moments of a cold start the app
 * does not yet know whether anyone is signed in. Events sent in that window would be attributed to
 * nobody, and `app_started` — the one event guaranteed to fall inside it — would be permanently
 * unattributable for every user. Events raised before this emits `true` are held and sent after.
 */
fun interface AnalyticsReadiness {
    fun isReady(): Flow<Boolean>
}

/**
 * Crash and non-fatal reporting, kept behind an interface for the same reason as [AnalyticsTracker]:
 * nothing below `:app` should have to compile against Firebase to say that something went wrong.
 */
interface CrashReporter {

    /** Breadcrumb text attached to whatever crash happens next. Never include statement contents. */
    fun log(message: String)

    /** Reports a caught throwable that did not crash the app but should not have happened. */
    fun recordNonFatal(throwable: Throwable)

    /**
     * Attaches the current identity to subsequent reports, or clears it when [accountHash] is null.
     *
     * The same hash the analytics events carry, so a crash can be lined up against the events that
     * led to it without either side ever holding an email address.
     */
    fun setIdentity(accountHash: String?)
}

/** Used in tests, previews, and any build where reporting is switched off. */
object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) = Unit
}

/** Used in tests and previews. */
object NoOpCrashReporter : CrashReporter {
    override fun log(message: String) = Unit
    override fun recordNonFatal(throwable: Throwable) = Unit
    override fun setIdentity(accountHash: String?) = Unit
}
