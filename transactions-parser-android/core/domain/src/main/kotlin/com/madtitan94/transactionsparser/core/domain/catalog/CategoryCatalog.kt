package com.madtitan94.transactionsparser.core.domain.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One suggested category from the public catalog.
 *
 * [key] is an identifier (`food`, `emi`), never the label and never something the user typed.
 * Analytics may send [key]. It must not send [name].
 */
data class SuggestedCategory(
    val key: String,
    val name: String,
    val sortOrder: Int
)

/**
 * A parsed catalog. [version] is the remote document's own version, not the Room schema.
 */
data class CategoryCatalog(
    val version: Int,
    val categories: List<SuggestedCategory>
) {
    companion object {
        /**
         * Parses a catalog document.
         *
         * Returns null for anything we should not display or store: malformed JSON, a missing
         * version, a key that is not a short identifier, a blank English name, or a duplicate.
         * Unknown fields are ignored, so a later catalog can add `matchTerms` without older
         * builds treating the document as corrupt.
         *
         * A document that parses is safe to replace the last good catalog. A document that does
         * not must be thrown away, even if Remote Config has already activated it.
         */
        fun parse(json: String): CategoryCatalog? {
            if (json.isBlank()) return null
            val file = try {
                catalogJson.decodeFromString<CatalogFile>(json)
            } catch (e: Exception) {
                null
            } ?: return null
            if (file.version < 1) return null

            val parsed = file.categories.mapIndexed { index, entry ->
                val key = entry.key.trim()
                val name = entry.names[ENGLISH]?.trim().orEmpty()
                if (!KEY_PATTERN.matches(key)) return null
                if (name.isEmpty() || name.length > MAX_NAME_LENGTH) return null
                SuggestedCategory(
                    key = key,
                    name = name,
                    sortOrder = entry.sortOrder ?: index
                )
            }
            if (parsed.size != parsed.map { it.key }.distinct().size) return null
            if (parsed.size != parsed.map { it.name.lowercase() }.distinct().size) return null
            return CategoryCatalog(
                version = file.version,
                categories = parsed.sortedWith(compareBy({ it.sortOrder }, { it.key }))
            )
        }
    }
}

/**
 * What to show, and whether the remote text should become the new last-good payload.
 *
 * [persistRemoteJson] is set only when [remoteJson] itself parsed. A bad payload leaves it null
 * so the caller keeps whatever it already stored.
 */
data class CatalogResolution(
    val catalog: CategoryCatalog,
    val persistRemoteJson: String?
)

/**
 * Picks the catalog to show.
 *
 * Remote text wins when it parses. Otherwise the last payload that parsed, then the copy bundled
 * in the app. The bundled catalog is the floor: a bad download cannot blank the suggestions.
 */
fun resolveCategoryCatalog(
    remoteJson: String,
    cachedJson: String?,
    bundled: CategoryCatalog
): CatalogResolution {
    CategoryCatalog.parse(remoteJson)?.let { remote ->
        return CatalogResolution(
            catalog = remote,
            persistRemoteJson = remoteJson.takeUnless { it == cachedJson }
        )
    }
    cachedJson?.let(CategoryCatalog::parse)?.let { cached ->
        return CatalogResolution(catalog = cached, persistRemoteJson = null)
    }
    return CatalogResolution(catalog = bundled, persistRemoteJson = null)
}

private const val ENGLISH = "en"
private const val MAX_NAME_LENGTH = 60

/** Short identifier. Spaces, capitals and punctuation cannot be a key, so a payee name cannot be one. */
private val KEY_PATTERN = Regex("^[a-z][a-z0-9_]{0,39}$")

private val catalogJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class CatalogFile(
    val version: Int,
    val categories: List<CatalogEntry>
)

@Serializable
private data class CatalogEntry(
    val key: String,
    val names: Map<String, String>,
    val sortOrder: Int? = null
)
