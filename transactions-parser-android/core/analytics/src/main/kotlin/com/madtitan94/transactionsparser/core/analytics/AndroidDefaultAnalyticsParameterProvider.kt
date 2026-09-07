package com.madtitan94.transactionsparser.core.analytics

import android.os.Build
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsParams
import com.madtitan94.transactionsparser.core.domain.analytics.DefaultAnalyticsParameterProvider

/**
 * The nine parameters attached to every event: who, which build, which device.
 *
 * The device fields are read once — `Build` is a set of compile-time constants baked into the ROM
 * and cannot change while the process lives — while the identity is read on every call, because it
 * changes the moment someone signs in or out.
 *
 * Nine of Firebase's twenty-five parameter slots are spent here, leaving sixteen for any single
 * event. No event in [com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsEvent] uses
 * more than three, and `AnalyticsContract` fails the build's tests if that ever stops being true.
 */
class AndroidDefaultAnalyticsParameterProvider(
    private val appName: String,
    private val appVersion: String,
    private val sessionTracker: AnalyticsSessionTracker
) : DefaultAnalyticsParameterProvider {

    private val deviceParams: Map<String, Any> = mapOf(
        AnalyticsParams.DEVICE to Build.DEVICE,
        AnalyticsParams.MANUFACTURER to Build.MANUFACTURER,
        AnalyticsParams.MODEL to Build.MODEL,
        AnalyticsParams.OS_VERSION to Build.VERSION.SDK_INT,
        AnalyticsParams.OS_RELEASE to Build.VERSION.RELEASE
    )

    override fun defaultParams(): Map<String, Any> = buildMap {
        put(AnalyticsParams.APP_NAME, appName)
        put(AnalyticsParams.APP_VERSION, appVersion)
        putAll(deviceParams)

        // Absent, not blank, when signed out. An empty string would become a real bucket in the
        // console and merge every signed-out user into one phantom account.
        val identity = sessionTracker.current()
        identity.accountHash?.let { put(AnalyticsParams.ACCOUNT_HASH, it) }
        identity.emailHash?.let { put(AnalyticsParams.EMAIL_HASH, it) }
    }
}
