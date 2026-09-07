package com.madtitan94.transactionsparser.core.analytics

import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsIdentity
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsReadiness
import com.madtitan94.transactionsparser.core.domain.analytics.CrashReporter
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The signed-in account reduced to what analytics is allowed to know about it. */
data class HashedIdentity(
    val accountHash: String?,
    val emailHash: String?
) {
    companion object {
        /** Nobody is signed in: both keys absent rather than blank. */
        val Anonymous = HashedIdentity(accountHash = null, emailHash = null)
    }
}

/**
 * Watches the stored session and keeps a synchronously-readable, hashed copy of who is signed in.
 *
 * Two problems, one class. The default-parameter provider is called on the sending path and must not
 * touch disk, so it needs the identity as a plain value — that is [current]. And the same collector
 * is the only place that knows when the session has been *read* at all, which is exactly the signal
 * the sender's gate needs, so this doubles as [AnalyticsReadiness].
 *
 * Splitting the two would mean reading DataStore twice and having the halves disagree during the
 * moment that matters most, which is the first second of a cold start.
 *
 * Crashlytics is updated from here for the same reason: identity changes in one place, so a crash
 * report and an analytics event can never disagree about whose session they belong to.
 */
class AnalyticsSessionTracker(
    sessions: Flow<UserSession?>,
    private val salt: String,
    private val crashReporter: CrashReporter,
    scope: CoroutineScope
) : AnalyticsReadiness {

    /** `null` means "not read yet", which is a different thing from [HashedIdentity.Anonymous]. */
    private val identity = MutableStateFlow<HashedIdentity?>(null)

    init {
        scope.launch {
            sessions.collect { session ->
                val hashed = if (session == null) {
                    HashedIdentity.Anonymous
                } else {
                    HashedIdentity(
                        accountHash = AnalyticsIdentity.hash(session.googleId, salt),
                        emailHash = AnalyticsIdentity.hash(session.email, salt)
                    )
                }
                identity.value = hashed
                crashReporter.setIdentity(hashed.accountHash)
            }
        }
    }

    override fun isReady(): Flow<Boolean> = identity.map { it != null }

    /**
     * The current identity, or [HashedIdentity.Anonymous] before the session has been read.
     *
     * The fallback is only reachable by a caller that ignores [isReady]; the sender does not, which
     * is why an event sent through it never lands anonymously by accident.
     */
    fun current(): HashedIdentity = identity.value ?: HashedIdentity.Anonymous
}
