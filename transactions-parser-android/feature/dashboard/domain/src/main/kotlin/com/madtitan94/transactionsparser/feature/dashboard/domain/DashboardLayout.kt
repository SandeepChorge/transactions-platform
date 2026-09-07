package com.madtitan94.transactionsparser.feature.dashboard.domain

/**
 * The saved shape of a user's Home: what exists, in what order, what is switched off, and which one
 * opens first.
 *
 * Stored as four independent facts rather than as a single resolved list, exactly as
 * `DashboardSpec` §3 describes: `order`, `disabled`, `default` and the user's own dashboards. Keeping
 * them separate is what lets a build that ships a fifth dashboard show it — a resolved list written
 * by an older version would pin Home to four entries forever.
 *
 * Everything a screen actually reads is derived below rather than stored, so the rules that matter —
 * unknown ids ignored, new dashboards appended and enabled, never an empty Home — are applied
 * identically on every read and are testable without a device.
 */
data class DashboardLayout(
    /** The user's chosen order, as far as it goes. Ids this build does not know are ignored. */
    val order: List<DashboardKey> = emptyList(),
    val disabled: Set<DashboardKey> = emptySet(),
    /** The starred dashboard, or null while the user has never chosen one. */
    val defaultKey: DashboardKey? = null,
    val custom: List<CustomDashboard> = emptyList()
) {

    /**
     * Every dashboard this build can draw for this user, in display order — switched off ones
     * included, because the manage screen has to show what it is offering to switch back on.
     *
     * Stored order first, then anything the order does not mention. That second half is the upgrade
     * path: a dashboard added in a later version, or one the user built a moment ago, arrives at the
     * end and switched on rather than staying invisible because it predates the saved order.
     */
    val all: List<DashboardDefinition>
        get() {
            val known = LinkedHashMap<DashboardKey, DashboardDefinition>()
            V1_DASHBOARDS.forEach { known[it.key] = it }
            custom.forEach { known[it.key] = it.toDefinition() }
            return (order + known.keys).distinct().mapNotNull { known[it] }
        }

    /**
     * What Home actually shows.
     *
     * An account that has switched everything off gets the whole set back instead: a Home screen
     * with nothing on it is indistinguishable from a broken one, and there would be no way left to
     * reach the screen that undoes it.
     */
    val enabled: List<DashboardDefinition>
        get() = all.filterNot { it.key in disabled }.ifEmpty { all }

    /**
     * Which dashboard Home opens on.
     *
     * The star only counts while the dashboard it points at is still switched on; otherwise Home
     * would open on something the user cannot see or swipe back to.
     */
    val defaultDashboard: DashboardKey?
        get() = defaultKey?.takeIf { starred -> enabled.any { it.key == starred } }
            ?: enabled.firstOrNull()?.key
}
