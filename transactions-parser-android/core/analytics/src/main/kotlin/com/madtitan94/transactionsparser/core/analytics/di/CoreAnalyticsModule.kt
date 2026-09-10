package com.madtitan94.transactionsparser.core.analytics.di

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.madtitan94.transactionsparser.core.analytics.AnalyticsSessionTracker
import com.madtitan94.transactionsparser.core.analytics.AndroidDefaultAnalyticsParameterProvider
import com.madtitan94.transactionsparser.core.analytics.FirebaseAnalyticsTracker
import com.madtitan94.transactionsparser.core.analytics.FirebaseCrashReporter
import com.madtitan94.transactionsparser.core.analytics.FirebaseLocalDataCleaner
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsReadiness
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsTracker
import com.madtitan94.transactionsparser.core.domain.analytics.CrashReporter
import com.madtitan94.transactionsparser.core.domain.analytics.DefaultAnalyticsParameterProvider
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * What `:app` has to tell the analytics layer about itself.
 *
 * A value object rather than a pile of Koin parameters, so the whole configuration of this feature
 * is one readable thing at the call site — and so a future build-type difference (analytics off in
 * debug, say) is a change to one expression rather than to the wiring.
 */
data class AnalyticsConfiguration(
    /** The app's own name, sent with every event so one Firebase project can host several apps. */
    val appName: String,
    /** `BuildConfig.VERSION_NAME`, which only `:app` has. */
    val appVersion: String,
    /**
     * The secret mixed into every identity hash. Not in this repository, which is public — see
     * [com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsIdentity].
     */
    val salt: String,
    /**
     * The kill switch. A lambda rather than a boolean because it is read per event, so whatever
     * ends up driving it later — a Settings toggle, a remote config flag — needs no change here.
     */
    val isEnabled: () -> Boolean,
    /** Throw on a malformed event rather than recording it. Debug builds only. */
    val failFast: Boolean
)

/**
 * A function rather than a `val` module, because none of this can be built without values that only
 * `:app` has: the version name, the salt, and the build type.
 */
fun coreAnalyticsModule(configuration: AnalyticsConfiguration): Module = module {
    single {
        FirebaseLocalDataCleaner(FirebaseAnalytics.getInstance(androidContext()), FirebaseCrashlytics.getInstance())
    }


    // Analytics outlives every screen, so its collectors belong to the process rather than to a
    // ViewModel. SupervisorJob so a failure in one collector cannot silently take down the other.
    single(named(ANALYTICS_SCOPE)) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single<CrashReporter> {
        FirebaseCrashReporter(
            crashlytics = FirebaseCrashlytics.getInstance(),
            isEnabled = configuration.isEnabled()
        )
    }

    single {
        AnalyticsSessionTracker(
            sessions = get<SessionStorage>().observeSession(),
            salt = configuration.salt,
            crashReporter = get(),
            scope = get(named(ANALYTICS_SCOPE))
        )
    }

    // The same instance under both contracts: the readiness gate and the identity cache are one
    // collector by design, and binding two would read the session twice and let them disagree.
    single<AnalyticsReadiness> { get<AnalyticsSessionTracker>() }

    single<DefaultAnalyticsParameterProvider> {
        AndroidDefaultAnalyticsParameterProvider(
            appName = configuration.appName,
            appVersion = configuration.appVersion,
            sessionTracker = get()
        )
    }

    single<AnalyticsTracker> {
        FirebaseAnalyticsTracker(
            firebase = FirebaseAnalytics.getInstance(androidContext()),
            defaults = get(),
            crashReporter = get(),
            isEnabled = configuration.isEnabled,
            failFast = configuration.failFast,
            readiness = get(),
            scope = get(named(ANALYTICS_SCOPE))
        )
    }
}

private const val ANALYTICS_SCOPE = "analyticsScope"
