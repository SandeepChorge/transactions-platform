package com.madtitan94.transactionsparser.feature.sessions.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import com.madtitan94.transactionsparser.core.domain.model.Payee
import org.junit.jupiter.api.Test

class AliasSuggesterTest {

    @Test
    fun `suggests nothing until something is typed`() {
        assertThat(AliasSuggester.suggest(payees("Swiggy", "Zomato"), "")).isEmpty()
        assertThat(AliasSuggester.suggest(payees("Swiggy", "Zomato"), "   ")).isEmpty()
    }

    @Test
    fun `matches anywhere in the alias, not only at the start`() {
        val suggestions = AliasSuggester.suggest(payees("Big Bazaar", "Zomato"), "baza")

        assertThat(suggestions.map { it.alias }).containsExactly("Big Bazaar")
    }

    @Test
    fun `ignores case in both directions`() {
        assertThat(AliasSuggester.suggest(payees("Swiggy"), "SWIG").map { it.alias })
            .containsExactly("Swiggy")
        assertThat(AliasSuggester.suggest(payees("SWIGGY"), "swig").map { it.alias })
            .containsExactly("SWIGGY")
    }

    @Test
    fun `puts the exact match first, then prefixes, then the rest`() {
        val payees = payees("Not Swiggy", "Swiggy Instamart", "Swiggy")

        val suggestions = AliasSuggester.suggest(payees, "Swiggy")

        assertThat(suggestions.map { it.alias })
            .containsExactly("Swiggy", "Swiggy Instamart", "Not Swiggy")
    }

    @Test
    fun `orders ties alphabetically so the list does not shuffle between keystrokes`() {
        val suggestions = AliasSuggester.suggest(payees("shop c", "Shop A", "shop b"), "shop")

        assertThat(suggestions.map { it.alias }).containsExactly("Shop A", "shop b", "shop c")
    }

    @Test
    fun `caps the list so it never buries the field being typed into`() {
        val many = payees(*Array(20) { "Shop $it" })

        assertThat(AliasSuggester.suggest(many, "shop")).hasSize(AliasSuggester.DEFAULT_LIMIT)
        assertThat(AliasSuggester.suggest(many, "shop", limit = 2)).hasSize(2)
    }

    @Test
    fun `a name nobody matches suggests nothing`() {
        assertThat(AliasSuggester.suggest(payees("Swiggy", "Zomato"), "rent")).isEmpty()
    }

    private fun payees(vararg aliases: String): List<Payee> =
        aliases.mapIndexed { index, alias ->
            Payee(id = index + 1L, alias = alias, categoryId = 1L)
        }
}
