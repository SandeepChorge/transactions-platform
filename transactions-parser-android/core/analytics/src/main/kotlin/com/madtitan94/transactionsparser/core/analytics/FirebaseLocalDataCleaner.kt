package com.madtitan94.transactionsparser.core.analytics

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.madtitan94.transactionsparser.core.domain.datasource.LocalDataCleaner
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result

/** Resets SDK data on this installation. Already uploaded reports require a separate request. */
class FirebaseLocalDataCleaner(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics
) : LocalDataCleaner {
    override suspend fun clearLocalData(): EmptyResult<DataError.Local> = try {
        analytics.setDefaultEventParameters(null)
        analytics.resetAnalyticsData()
        crashlytics.setUserId("")
        // These SDK APIs enqueue their own disk cleanup and return no completion handle.
        crashlytics.deleteUnsentReports()
        Result.Success(Unit)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Result.Error(DataError.Local.UNKNOWN)
    }
}
