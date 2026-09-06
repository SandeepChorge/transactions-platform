package com.madtitan94.transactionsparser.feature.dashboard.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardId
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardPreferences
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardRange
import com.madtitan94.transactionsparser.feature.dashboard.domain.V1_DASHBOARDS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dashboardDataStore by preferencesDataStore(name = "dashboard_store")

/** Separates the account from the setting inside one key. No Google id contains it. */
private const val OWNER_SEPARATOR = "::"

/** The owner a signed-out read writes under, so it can never collide with a real account. */
private const val NO_OWNER = "__none__"

/**
 * Dashboard preferences in their own DataStore file, namespaced per account.
 *
 * One file with account-prefixed keys rather than a file per account: DataStore holds a file open
 * per instance, and an app that has seen three accounts would otherwise carry three open handles for
 * settings that are read once per screen. The prefix is resolved from [SessionStorage] here rather
 * than passed in by a caller, mirroring how `ActiveAccountProvider` scopes the Room data sources —
 * a screen cannot forget to scope itself if it never sees the key.
 *
 * Every read is defensive. These strings are only ever written by this class, but a downgrade can
 * leave a value this build has never heard of, and a wrong-but-plausible dashboard is a better
 * outcome on launch than a crash.
 */
class DataStoreDashboardPreferences(
    private val context: Context,
    private val sessionStorage: SessionStorage
) : DashboardPreferences {

    private object Keys {
        const val RANGE = "range"
        const val RANGE_FROM = "range_from"
        const val RANGE_TO = "range_to"
        const val ORDER = "dashboard_order"
        const val DISABLED = "dashboard_disabled"
        const val DEFAULT = "dashboard_default"
    }

    private fun ownerId(): Flow<String> = sessionStorage.observeSession()
        .map { it?.googleId ?: NO_OWNER }
        .distinctUntilChanged()

    private fun <T> scoped(read: (Preferences, (String) -> Preferences.Key<String>) -> T): Flow<T> =
        combine(ownerId(), context.dashboardDataStore.data) { owner, prefs ->
            read(prefs) { name -> stringPreferencesKey("$owner$OWNER_SEPARATOR$name") }
        }.distinctUntilChanged()

    override fun observeRange(): Flow<DashboardRange> = scoped { prefs, key ->
        when (prefs[key(Keys.RANGE)]) {
            null -> DashboardRange.Default
            DashboardRange.Today.key -> DashboardRange.Today
            DashboardRange.ThisWeek.key -> DashboardRange.ThisWeek
            DashboardRange.ThisMonth.key -> DashboardRange.ThisMonth
            DashboardRange.LastMonth.key -> DashboardRange.LastMonth
            DashboardRange.AllTime.key -> DashboardRange.AllTime
            "CUSTOM" -> customRange(prefs[key(Keys.RANGE_FROM)], prefs[key(Keys.RANGE_TO)])
            // A key from a newer build, or a corrupted value. Either way the user gets the default
            // period rather than an exception on the first frame of the app.
            else -> DashboardRange.Default
        }
    }

    /**
     * A stored custom range is only honoured when both ends parse and the span is not inverted.
     *
     * Half a custom range is not a range. Falling back to the default is visible and recoverable;
     * a from-date after its to-date would produce an empty period on every widget at once, which
     * reads as "you have no transactions" rather than as a broken preference.
     */
    private fun customRange(from: String?, to: String?): DashboardRange {
        val start = from?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = to?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (start == null || end == null || end.isBefore(start)) return DashboardRange.Default
        return DashboardRange.Custom(start, end)
    }

    override suspend fun setRange(range: DashboardRange) {
        val owner = ownerId().first()
        context.dashboardDataStore.edit { prefs ->
            fun key(name: String) = stringPreferencesKey("$owner$OWNER_SEPARATOR$name")
            prefs[key(Keys.RANGE)] = range.key
            if (range is DashboardRange.Custom) {
                prefs[key(Keys.RANGE_FROM)] = range.fromDate.toString()
                prefs[key(Keys.RANGE_TO)] = range.toDateInclusive.toString()
            } else {
                // Clear the endpoints rather than leaving them behind: a stale pair read back after
                // a later downgrade would resurrect a period the user has since moved off.
                prefs.remove(key(Keys.RANGE_FROM))
                prefs.remove(key(Keys.RANGE_TO))
            }
        }
    }

    override fun observeEnabledDashboards(): Flow<List<DashboardId>> = scoped { prefs, key ->
        val known = V1_DASHBOARDS.map { it.id }
        val stored = prefs[key(Keys.ORDER)].orEmpty().split(',').mapNotNull { it.toDashboardId() }
        val disabled = prefs[key(Keys.DISABLED)].orEmpty().split(',').mapNotNull { it.toDashboardId() }

        // Stored order first, then anything this build added since it was written. A dashboard
        // shipped in a later version arrives switched on and appended, per DashboardSpec §3 — it
        // must not stay invisible just because the preference predates it.
        val ordered = (stored + known).distinct()
        val enabled = ordered.filterNot { it in disabled }
        enabled.ifEmpty { known }
    }

    override fun observeDefaultDashboard(): Flow<DashboardId> = combine(
        scoped { prefs, key -> prefs[key(Keys.DEFAULT)]?.toDashboardId() },
        observeEnabledDashboards()
    ) { stored, enabled ->
        // The starred dashboard only counts while it is still switched on; otherwise the chip row
        // would open on a dashboard the user cannot see.
        stored?.takeIf { it in enabled } ?: enabled.first()
    }.distinctUntilChanged()

    private fun String.toDashboardId(): DashboardId? =
        runCatching { DashboardId.valueOf(trim()) }.getOrNull()
}
