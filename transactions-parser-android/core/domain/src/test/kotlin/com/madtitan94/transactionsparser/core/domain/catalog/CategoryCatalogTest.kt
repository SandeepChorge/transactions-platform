package com.madtitan94.transactionsparser.core.domain.catalog

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test

class CategoryCatalogTest {

    @Test
    fun `bundled catalog is the twelve starter categories in order`() {
        val catalog = BundledCategoryCatalog.load()

        assertThat(catalog.version).isEqualTo(1)
        assertThat(catalog.categories.map { it.name }).containsExactly(
            "Food",
            "Groceries",
            "Rent",
            "Bills & Utilities",
            "Fuel",
            "Transport",
            "Shopping",
            "Health",
            "Education",
            "Entertainment",
            "EMI/Loans",
            "Travel"
        )
        assertThat(catalog.categories.map { it.key }).containsExactly(
            "food",
            "groceries",
            "rent",
            "bills",
            "fuel",
            "transport",
            "shopping",
            "health",
            "education",
            "entertainment",
            "emi",
            "travel"
        )
    }

    @Test
    fun `unknown fields are ignored and english is the only name read`() {
        val catalog = CategoryCatalog.parse(
            """
            {
              "version": 4,
              "note": "ignored",
              "categories": [
                {
                  "key": "food",
                  "names": { "en": "Food", "hi": "खाना" },
                  "sortOrder": 0,
                  "matchTerms": ["swiggy", "zomato"]
                }
              ]
            }
            """.trimIndent()
        )

        assertThat(catalog).isNotNull()
        assertThat(catalog!!.version).isEqualTo(4)
        assertThat(catalog.categories.single().name).isEqualTo("Food")
    }

    @Test
    fun `sort order is honoured when the file is not already sorted`() {
        val catalog = CategoryCatalog.parse(
            """
            {
              "version": 1,
              "categories": [
                { "key": "travel", "names": { "en": "Travel" }, "sortOrder": 1 },
                { "key": "food", "names": { "en": "Food" }, "sortOrder": 0 }
              ]
            }
            """.trimIndent()
        )

        assertThat(catalog!!.categories.map { it.key }).containsExactly("food", "travel")
    }

    @Test
    fun `a missing sort order falls back to file order`() {
        val catalog = CategoryCatalog.parse(
            """
            {
              "version": 1,
              "categories": [
                { "key": "rent", "names": { "en": "Rent" } },
                { "key": "fuel", "names": { "en": "Fuel" } }
              ]
            }
            """.trimIndent()
        )

        assertThat(catalog!!.categories.map { it.key }).containsExactly("rent", "fuel")
    }

    @Test
    fun `malformed json is not a catalog`() {
        assertThat(CategoryCatalog.parse("")).isNull()
        assertThat(CategoryCatalog.parse("{")).isNull()
        assertThat(CategoryCatalog.parse("""{ "categories": [] }""")).isNull()
        assertThat(CategoryCatalog.parse("""{ "version": "one", "categories": [] }""")).isNull()
    }

    @Test
    fun `a key that is not an identifier rejects the whole document`() {
        assertThat(parseOne(key = "Food")).isNull()
        assertThat(parseOne(key = "morning tea")).isNull()
        assertThat(parseOne(key = "AWDHOOT")).isNull()
        assertThat(parseOne(key = "emi/loans")).isNull()
    }

    @Test
    fun `a blank or missing english name rejects the whole document`() {
        assertThat(
            CategoryCatalog.parse(
                """{ "version": 1, "categories": [ { "key": "food", "names": { "en": "  " } } ] }"""
            )
        ).isNull()
        assertThat(
            CategoryCatalog.parse(
                """{ "version": 1, "categories": [ { "key": "food", "names": { "hi": "खाना" } } ] }"""
            )
        ).isNull()
    }

    @Test
    fun `duplicate keys or names reject the whole document`() {
        assertThat(
            CategoryCatalog.parse(
                """
                {
                  "version": 1,
                  "categories": [
                    { "key": "food", "names": { "en": "Food" } },
                    { "key": "food", "names": { "en": "Meals" } }
                  ]
                }
                """.trimIndent()
            )
        ).isNull()
        assertThat(
            CategoryCatalog.parse(
                """
                {
                  "version": 1,
                  "categories": [
                    { "key": "food", "names": { "en": "Food" } },
                    { "key": "meals", "names": { "en": "food" } }
                  ]
                }
                """.trimIndent()
            )
        ).isNull()
    }

    @Test
    fun `an empty category list is a valid catalog`() {
        val catalog = CategoryCatalog.parse("""{ "version": 2, "categories": [] }""")
        assertThat(catalog).isEqualTo(CategoryCatalog(version = 2, categories = emptyList()))
    }

    @Test
    fun `a bad remote payload does not replace the last good catalog`() {
        val bundled = CategoryCatalog(version = 1, categories = listOf(food))
        val cached = """{ "version": 5, "categories": [ { "key": "rent", "names": { "en": "Rent" } } ] }"""

        val resolution = resolveCategoryCatalog(remoteJson = "{", cachedJson = cached, bundled = bundled)

        assertThat(resolution.catalog.categories.map { it.key }).containsExactly("rent")
        assertThat(resolution.catalog.version).isEqualTo(5)
        assertThat(resolution.persistRemoteJson).isNull()
    }

    @Test
    fun `a remote payload that parses replaces the cache and is what gets stored`() {
        val bundled = CategoryCatalog(version = 1, categories = listOf(food))
        val remote = """{ "version": 6, "categories": [ { "key": "fuel", "names": { "en": "Fuel" } } ] }"""

        val resolution = resolveCategoryCatalog(remoteJson = remote, cachedJson = "stale", bundled = bundled)

        assertThat(resolution.catalog.version).isEqualTo(6)
        assertThat(resolution.catalog.categories.single().key).isEqualTo("fuel")
        assertThat(resolution.persistRemoteJson).isEqualTo(remote)
    }

    @Test
    fun `a remote payload identical to the cache is not written again`() {
        val json = """{ "version": 6, "categories": [ { "key": "fuel", "names": { "en": "Fuel" } } ] }"""
        val resolution = resolveCategoryCatalog(
            remoteJson = json,
            cachedJson = json,
            bundled = CategoryCatalog(version = 1, categories = emptyList())
        )

        assertThat(resolution.persistRemoteJson).isNull()
        assertThat(resolution.catalog.version).isEqualTo(6)
    }

    @Test
    fun `with no remote and no cache the bundled catalog is the floor`() {
        val bundled = CategoryCatalog(version = 1, categories = listOf(food))

        val resolution = resolveCategoryCatalog(remoteJson = "", cachedJson = null, bundled = bundled)

        assertThat(resolution.catalog).isEqualTo(bundled)
        assertThat(resolution.persistRemoteJson).isNull()
    }

    @Test
    fun `a bad cache falls through to the bundled catalog`() {
        val bundled = CategoryCatalog(version = 1, categories = listOf(food))

        val resolution = resolveCategoryCatalog(
            remoteJson = "not json",
            cachedJson = "also not json",
            bundled = bundled
        )

        assertThat(resolution.catalog).isEqualTo(bundled)
        assertThat(resolution.persistRemoteJson).isNull()
    }

    private fun parseOne(key: String) = CategoryCatalog.parse(
        """{ "version": 1, "categories": [ { "key": "$key", "names": { "en": "Food" } } ] }"""
    )

    private val food = SuggestedCategory(key = "food", name = "Food", sortOrder = 0)
}
