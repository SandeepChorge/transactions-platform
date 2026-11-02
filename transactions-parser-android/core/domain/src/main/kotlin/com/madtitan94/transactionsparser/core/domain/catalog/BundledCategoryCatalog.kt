package com.madtitan94.transactionsparser.core.domain.catalog

/**
 * The catalog shipped inside the app.
 *
 * This is the Remote Config in-app default and the offline floor. The same file is what a
 * developer pastes over the `default_categories` parameter; keeping one copy means the fallback
 * and the console document cannot drift by accident.
 */
object BundledCategoryCatalog {
    fun json(): String {
        val stream = BundledCategoryCatalog::class.java.getResourceAsStream("/default_categories.json")
            ?: error("Bundled category catalog is missing from the app.")
        return stream.bufferedReader().use { it.readText() }
    }

    fun load(): CategoryCatalog =
        CategoryCatalog.parse(json()) ?: error("Bundled category catalog does not parse.")
}
