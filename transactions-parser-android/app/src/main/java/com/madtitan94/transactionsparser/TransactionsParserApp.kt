package com.madtitan94.transactionsparser

import android.app.Application
import com.madtitan94.transactionsparser.core.analytics.di.AnalyticsConfiguration
import com.madtitan94.transactionsparser.core.analytics.di.coreAnalyticsModule
import com.madtitan94.transactionsparser.core.database.di.coreDatabaseModule
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsEvent
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsTracker
import com.madtitan94.transactionsparser.core.parsing.di.coreParsingModule
import com.madtitan94.transactionsparser.core.pdf.di.corePdfModule
import com.madtitan94.transactionsparser.feature.auth.data.di.authDataModule
import com.madtitan94.transactionsparser.feature.auth.presentation.di.authPresentationModule
import com.madtitan94.transactionsparser.feature.categories.presentation.di.categoriesPresentationModule
import com.madtitan94.transactionsparser.feature.dashboard.data.di.dashboardDataModule
import com.madtitan94.transactionsparser.feature.dashboard.presentation.di.dashboardPresentationModule
import com.madtitan94.transactionsparser.feature.profile.data.di.profileDataModule
import com.madtitan94.transactionsparser.feature.profile.presentation.di.profilePresentationModule
import com.madtitan94.transactionsparser.feature.sessions.presentation.di.sessionsPresentationModule
import com.madtitan94.transactionsparser.feature.settings.data.di.settingsDataModule
import com.madtitan94.transactionsparser.feature.settings.presentation.di.settingsPresentationModule
import com.madtitan94.transactionsparser.feature.upload.data.di.uploadDataModule
import com.madtitan94.transactionsparser.feature.upload.domain.di.uploadDomainModule
import com.madtitan94.transactionsparser.feature.upload.presentation.di.uploadPresentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.android.get
import org.koin.core.context.startKoin

class TransactionsParserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TransactionsParserApp)
            modules(
                // core
                coreAnalyticsModule(analyticsConfiguration()),
                coreDatabaseModule,
                coreParsingModule,
                corePdfModule,
                // app
                appModule,
                // features
                authDataModule,
                authPresentationModule,
                profileDataModule,
                profilePresentationModule,
                uploadDomainModule,
                uploadDataModule,
                uploadPresentationModule,
                sessionsPresentationModule,
                categoriesPresentationModule,
                dashboardDataModule,
                dashboardPresentationModule,
                settingsDataModule,
                settingsPresentationModule
            )
        }

        // Raised immediately, before the stored session has been read. It is held by the tracker's
        // readiness gate and sent once the identity is known — which is the entire reason that gate
        // exists, since otherwise every user's first event of every launch is anonymous.
        get<AnalyticsTracker>().track(AnalyticsEvent.AppStarted)
    }

    /**
     * Analytics is on in every build type, including debug.
     *
     * The alternative — production only, the way the Milagro service gates on its base URL — assumes
     * a user base large enough for a handful of developer sessions to distort the numbers. This app
     * does not have one yet, and being able to watch events arrive in DebugView while working on a
     * feature is worth more than that precision. Revisit at the point real installs outnumber ours;
     * it is a one-line change to `isEnabled`.
     */
    private fun analyticsConfiguration() = AnalyticsConfiguration(
        appName = getString(R.string.app_name),
        appVersion = BuildConfig.VERSION_NAME,
        salt = BuildConfig.ANALYTICS_SALT,
        // The hook a Settings toggle will drive later. Read per event, so switching it to a stored
        // preference needs no change anywhere else.
        isEnabled = { true },
        // A malformed event stops a developer at the call site and is only ever recorded in
        // release. Crashing a user's app over an analytics parameter would be indefensible.
        failFast = BuildConfig.DEBUG
    )
}
