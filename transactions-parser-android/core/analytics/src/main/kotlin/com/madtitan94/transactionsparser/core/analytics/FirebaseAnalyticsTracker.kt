package com.madtitan94.transactionsparser.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsContract
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsEvent
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsParams
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsReadiness
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsTracker
import com.madtitan94.transactionsparser.core.domain.analytics.CrashReporter
import com.madtitan94.transactionsparser.core.domain.analytics.DefaultAnalyticsParameterProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The app's single sender of analytics events.
 *
 * Three things happen between [track] and Firebase, and each exists because of a specific way this
 * goes wrong otherwise:
 *
 * 1. **A readiness gate.** Events raised before the stored session has been read are held rather
 *    than sent, because sending them would permanently attribute every user's first events — always
 *    including `app_started` — to nobody.
 * 2. **Validation.** Firebase drops a malformed event silently. [AnalyticsContract] turns that into
 *    a loud failure during development and a recorded non-fatal in release.
 * 3. **Default-parameter diffing.** `setDefaultEventParameters` is a comparatively expensive call
 *    and the parameters change only when the user signs in or out, so it runs when they actually
 *    differ rather than once per event.
 *
 * @param failFast throw on an invalid event instead of recording it. True in debug builds only: an
 *   analytics mistake should stop a developer immediately and must never crash a user's app,
 *   least of all one holding their financial history.
 */
class FirebaseAnalyticsTracker(
    private val firebase: FirebaseAnalytics,
    private val defaults: DefaultAnalyticsParameterProvider,
    private val crashReporter: CrashReporter,
    private val isEnabled: () -> Boolean,
    private val failFast: Boolean,
    readiness: AnalyticsReadiness,
    scope: CoroutineScope
) : AnalyticsTracker {

    /**
     * Buffers events raised before the gate opens.
     *
     * `DROP_OLDEST` rather than suspending, because [track] must never block a UI callback. If the
     * app somehow raises more than [QUEUE_CAPACITY] events before the session is read, losing the
     * oldest is the right failure: the newest ones describe what the user is doing now.
     */
    private val queue = MutableSharedFlow<AnalyticsEvent>(
        replay = QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** The last default-parameter map actually pushed to Firebase, for the diff described above. */
    private var lastAppliedDefaults: Map<String, Any>? = null

    init {
        scope.launch {
            readiness.isReady().first { it }
            queue.collect(::send)
        }
    }

    override fun track(event: AnalyticsEvent) {
        val defaultParams = defaults.defaultParams()
        val violation = AnalyticsContract.violationOf(event.name, event.params, defaultParams)
        if (violation != null) {
            if (failFast) throw IllegalArgumentException(violation)
            crashReporter.recordNonFatal(IllegalArgumentException(violation))
            return
        }
        if (!isEnabled()) return
        queue.tryEmit(event)
    }

    private fun send(event: AnalyticsEvent) {
        // Re-read rather than reuse the map from `track`: an event queued before sign-in should
        // carry the identity it has by the time it is actually sent, not the absent one it was
        // raised with. This is the whole reason the gate holds events instead of dropping them.
        applyDefaults(defaults.defaultParams())
        firebase.logEvent(event.name, event.params.toBundle())
    }

    private fun applyDefaults(current: Map<String, Any>) {
        if (current == lastAppliedDefaults) return

        // The account hash doubles as Firebase's own user id, which is what makes per-user metrics
        // and Crashlytics line up. It stays in the parameter map as well — with nine defaults there
        // is room inside the limit of 25, and a raw event export that carries its own identity is
        // far easier to reason about than one that depends on a join.
        firebase.setUserId(current[AnalyticsParams.ACCOUNT_HASH] as? String)
        firebase.setDefaultEventParameters(current.toBundle())
        lastAppliedDefaults = current
    }

    /**
     * Firebase parameters are strings, longs and doubles — there is no boolean parameter type, and
     * `putBoolean` produces a value the console cannot filter on. Booleans go as `"true"`/`"false"`
     * so they stay usable as a dimension.
     */
    private fun Map<String, Any>.toBundle(): Bundle = Bundle().apply {
        forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putString(key, value.toString())
                is Int -> putLong(key, value.toLong())
                is Long -> putLong(key, value)
                is Short -> putLong(key, value.toLong())
                is Byte -> putLong(key, value.toLong())
                is Double -> putDouble(key, value)
                is Float -> putDouble(key, value.toDouble())
                // Unreachable: AnalyticsContract rejects every other type before this runs. Kept
                // total rather than throwing, so a future contract change cannot crash a send.
                else -> putString(key, value.toString())
            }
        }
    }

    private companion object {
        /**
         * Generous on purpose. The gate closes for a single DataStore read, during which the app
         * realistically raises one event, so this will never fill — and if it does, that is a bug
         * worth having the newest events to diagnose.
         */
        const val QUEUE_CAPACITY = 32
    }
}
