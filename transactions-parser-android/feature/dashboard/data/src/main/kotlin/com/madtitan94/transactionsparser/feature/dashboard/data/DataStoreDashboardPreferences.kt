package com.madtitan94.transactionsparser.feature.dashboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madtitan94.transactionsparser.core.domain.datasource.LocalDataCleaner
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.feature.dashboard.domain.CustomDashboard
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardKey
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardLayout
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardPreferences
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardRange
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardWidgetId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.dashboardDataStore by preferencesDataStore(name = "dashboard_store")

/** Separates the account from the setting inside one key. No Google id contains it. */
private const val OWNER_SEPARATOR = "::"

/** The owner a signed-out read writes under, so it can never collide with a real account. */
private const val NO_OWNER = "__none__"

/** Separates ids inside the order and disabled lists. Neither an enum name nor a UUID contains it. */
private const val ID_SEPARATOR = ","

/**
 * Lenient on purpose: this decodes a file the user's own device wrote, and a field added in a later
 * version must not make every dashboard they built unreadable after a downgrade.
 */
private val json = Json { ignoreUnknownKeys = true }

/**
 * A user-built dashboard on disk.
 *
 * JSON rather than a delimited string because one of these fields is free text the user typed: a
 * dashboard called "Food, drink" would split a comma-joined record in half, and escaping by hand is
 * a bug waiting for the first name with a backslash in it.
 */
@Serializable
private data class StoredCustomDashboard(
    val id: String,
    val name: String,
    val widgets: List<String>
)

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
    context: Context,
    private val sessionStorage: SessionStorage,
    private val dataStore: DataStore<Preferences> = context.dashboardDataStore
) : DashboardPreferences, LocalDataCleaner {

    override suspend fun clearLocalData(): EmptyResult<DataError.Local> = try {
        dataStore.edit { it.clear() }
        Result.Success(Unit)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Result.Error(DataError.Local.UNKNOWN)
    }

    private object Keys {
        const val RANGE = "range"
        const val RANGE_FROM = "range_from"
        const val RANGE_TO = "range_to"
        const val ORDER = "dashboard_order"
        const val DISABLED = "dashboard_disabled"
        const val DEFAULT = "dashboard_default"
        const val CUSTOM = "dashboard_custom"
        const val DISMISSED_ANOMALIES = "dismissed_anomalies"
    }

    private fun ownerId(): Flow<String> = sessionStorage.observeSession()
        .map { it?.googleId ?: NO_OWNER }
        .distinctUntilChanged()

    private fun <T> scoped(read: (Preferences, (String) -> Preferences.Key<String>) -> T): Flow<T> =
        combine(ownerId(), dataStore.data) { owner, prefs ->
            read(prefs) { name -> stringPreferencesKey("$owner$OWNER_SEPARATOR$name") }
        }.distinctUntilChanged()

    /**
     * Reads the current account's keys, edits them, and writes them back.
     *
     * Every write goes through this rather than opening `edit` directly, so no caller can write an
     * unscoped key and leave one account's layout visible to another.
     */
    private suspend fun editScoped(block: (MutablePreferences, (String) -> Preferences.Key<String>) -> Unit) {
        val owner = ownerId().first()
        dataStore.edit { prefs ->
            block(prefs) { name -> stringPreferencesKey("$owner$OWNER_SEPARATOR$name") }
        }
    }

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

    override suspend fun setRange(range: DashboardRange) = editScoped { prefs, key ->
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

    /**
     * The four stored facts, unresolved.
     *
     * Nothing is filtered or defaulted here beyond dropping ids that will not parse — the rules
     * about what Home shows live on [DashboardLayout], where they can be tested without a device
     * and cannot drift between the screens that read them.
     */
    override fun observeLayout(): Flow<DashboardLayout> = scoped { prefs, key ->
        DashboardLayout(
            order = prefs[key(Keys.ORDER)].toKeys(),
            disabled = prefs[key(Keys.DISABLED)].toKeys().toSet(),
            defaultKey = prefs[key(Keys.DEFAULT)]?.let(DashboardKey::parse),
            custom = prefs[key(Keys.CUSTOM)].toCustomDashboards()
        )
    }

    override suspend fun setDashboardOrder(order: List<DashboardKey>) = editScoped { prefs, key ->
        prefs[key(Keys.ORDER)] = order.joinToString(ID_SEPARATOR) { it.storageId }
    }

    /**
     * Stores the *disabled* set rather than the enabled one, per `DashboardSpec` §3.
     *
     * Which way round this goes is the whole upgrade story: an enabled set written by today's build
     * would not name a dashboard shipped next year, so that dashboard would arrive switched off and
     * the user would never learn it existed. Naming what is switched off means anything new is on.
     */
    override suspend fun setDashboardEnabled(key: DashboardKey, enabled: Boolean) =
        editScoped { prefs, prefKey ->
            val current = prefs[prefKey(Keys.DISABLED)].toKeys().toMutableSet()
            if (enabled) current.remove(key) else current.add(key)
            prefs[prefKey(Keys.DISABLED)] = current.joinToString(ID_SEPARATOR) { it.storageId }
        }

    override suspend fun setDefaultDashboard(key: DashboardKey) = editScoped { prefs, prefKey ->
        prefs[prefKey(Keys.DEFAULT)] = key.storageId
    }

    override suspend fun saveCustomDashboard(dashboard: CustomDashboard) = editScoped { prefs, key ->
        val existing = prefs[key(Keys.CUSTOM)].toCustomDashboards()
        // Replaced in place rather than removed and appended, so editing a dashboard does not send
        // it to the end of the user's Home behind everything they put after it.
        val index = existing.indexOfFirst { it.id == dashboard.id }
        val updated = if (index >= 0) {
            existing.toMutableList().apply { set(index, dashboard) }
        } else {
            existing + dashboard
        }
        prefs[key(Keys.CUSTOM)] = updated.encode()
    }

    override suspend fun deleteCustomDashboard(id: String) = editScoped { prefs, key ->
        prefs[key(Keys.CUSTOM)] = prefs[key(Keys.CUSTOM)].toCustomDashboards()
            .filterNot { it.id == id }
            .encode()
        // The order and disabled lists are deliberately left alone. They are read through
        // DashboardKey.parse against the dashboards that exist, so a dangling id is already ignored,
        // and rewriting three keys to tidy up one deletion is three chances to corrupt a layout.
    }

    /**
     * Row ids the user has waved off, as a delimited list.
     *
     * A delimited string rather than DataStore's own `stringSetPreferencesKey`, so that every value
     * in this file goes through the same account-prefixed [scoped] read and there is exactly one way
     * a key can be built. A set of ids is the one shape where the delimiter is safe without
     * escaping: the members are digits.
     *
     * Values that do not parse are dropped rather than thrown on, like every other read here — a
     * corrupted dismissal costs the user one callout reappearing, which is recoverable by dismissing
     * it again.
     */
    override fun observeDismissedAnomalies(): Flow<Set<Long>> = scoped { prefs, key ->
        prefs[key(Keys.DISMISSED_ANOMALIES)].orEmpty()
            .split(ID_SEPARATOR)
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    override suspend fun dismissAnomaly(transactionId: Long) = editScoped { prefs, key ->
        val current = prefs[key(Keys.DISMISSED_ANOMALIES)].orEmpty()
            .split(ID_SEPARATOR)
            .mapNotNull { it.toLongOrNull() }
            .toMutableSet()
        current.add(transactionId)
        prefs[key(Keys.DISMISSED_ANOMALIES)] = current.joinToString(ID_SEPARATOR)
    }

    private fun String?.toKeys(): List<DashboardKey> =
        orEmpty().split(ID_SEPARATOR).mapNotNull { DashboardKey.parse(it) }

    /**
     * Unreadable JSON yields no dashboards rather than an exception.
     *
     * The user loses the ones they built, which is bad; the alternative is an app that cannot open
     * its Home screen, which is worse and unrecoverable without clearing app data.
     */
    private fun String?.toCustomDashboards(): List<CustomDashboard> {
        val raw = this?.takeIf { it.isNotBlank() } ?: return emptyList()
        val stored = runCatching { json.decodeFromString<List<StoredCustomDashboard>>(raw) }
            .getOrElse { return emptyList() }
        return stored.mapNotNull { entry ->
            // A widget this build has never heard of is dropped, not fatal — the rest of the
            // dashboard still renders. One with nothing left is dropped entirely, because an empty
            // dashboard is a blank page the user cannot tell from a failure.
            val widgets = entry.widgets.mapNotNull { name ->
                runCatching { DashboardWidgetId.valueOf(name) }.getOrNull()
            }
            if (widgets.isEmpty()) null else CustomDashboard(entry.id, entry.name, widgets)
        }
    }

    private fun List<CustomDashboard>.encode(): String = json.encodeToString(
        map { StoredCustomDashboard(it.id, it.name, it.widgets.map(DashboardWidgetId::name)) }
    )
}
