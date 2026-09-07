package com.madtitan94.transactionsparser.core.domain.search

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.model.SearchQuery
import org.junit.jupiter.api.Test

/**
 * The judgement half of the search, pinned without a database.
 *
 * Everything here decides what the user *meant*, which is the part a SQL test cannot check: the
 * query either returns rows or it does not, and a wrong reading of "1250" produces a perfectly
 * healthy result set for the wrong question.
 */
class SearchQueryTest {

    @Test
    fun `a whole rupee figure matches every paise inside that rupee`() {
        // Someone typing 1250 off a statement means the ₹1,250 charge, and a card charge of
        // ₹1,250.37 is the one they are looking at. Matching the exact paise would find nothing.
        val query = SearchQuery.parse("1250")

        assertThat(query.amountFromPaise).isEqualTo(125_000L)
        assertThat(query.amountToPaise).isEqualTo(125_099L)
    }

    @Test
    fun `typing the decimals narrows it back to the exact paise`() {
        val query = SearchQuery.parse("1250.37")

        assertThat(query.amountFromPaise).isEqualTo(125_037L)
        assertThat(query.amountToPaise).isEqualTo(125_037L)
    }

    @Test
    fun `a single decimal place is read as tens of paise, not as paise`() {
        // 1250.4 is ₹1,250.40 — the same figure a statement would print as 1250.40. Reading the
        // digit as paise would look for ₹1,250.04 and quietly find the wrong charge or none.
        assertThat(SearchQuery.parse("1250.4").amountFromPaise).isEqualTo(125_040L)
    }

    @Test
    fun `currency decoration is stripped before the amount is read`() {
        listOf("₹1,250", "Rs 1250", "Rs.1250", " 1,250 ").forEach { typed ->
            assertThat(SearchQuery.parse(typed).amountFromPaise).isEqualTo(125_000L)
        }
    }

    @Test
    fun `a payee name is not an amount`() {
        val query = SearchQuery.parse("swiggy")

        assertThat(query.amountFromPaise).isNull()
        assertThat(query.amountToPaise).isNull()
    }

    @Test
    fun `a reference with digits and letters is not an amount`() {
        // The amount branch has to stay off for these, or every UTR fragment would also drag in
        // every transaction that happened to cost that many rupees.
        assertThat(SearchQuery.parse("UTR12345").amountFromPaise).isNull()
    }

    @Test
    fun `a figure too large to be a transaction yields no amount rather than a wrapped one`() {
        // Truncating would search for whatever the overflow happened to land on, which is a real
        // amount belonging to some other charge entirely.
        assertThat(SearchQuery.parse("999999999999999999999").amountFromPaise).isNull()
    }

    @Test
    fun `the text half survives an amount being parsed out of it`() {
        // Both matches are offered: 1250 is a plausible amount and a plausible fragment of a UTR,
        // and nothing in a search box says which the user meant.
        val query = SearchQuery.parse("1250")

        assertThat(query.text).isEqualTo("1250")
        assertThat(query.likePattern()).isEqualTo("%1250%")
    }

    @Test
    fun `wildcards the user types are escaped rather than honoured`() {
        // Unescaped, a typed % matches every row in the account and presents it as a search result.
        assertThat(SearchQuery.parse("100%").likePattern()).isEqualTo("%100\\%%")
        assertThat(SearchQuery.parse("a_b").likePattern()).isEqualTo("%a\\_b%")
        assertThat(SearchQuery.parse("""a\b""").likePattern()).isEqualTo("""%a\\b%""")
    }

    @Test
    fun `one character is not a search`() {
        // The result set at one character is the account, which is the export rather than a search.
        assertThat(SearchQuery.parse("s").isBlank).isTrue()
        assertThat(SearchQuery.parse("  ").isBlank).isTrue()
        assertThat(SearchQuery.parse("sw").isBlank).isFalse()
    }

    @Test
    fun `surrounding whitespace is trimmed off the text`() {
        assertThat(SearchQuery.parse("  swiggy  ").text).isEqualTo("swiggy")
    }
}
