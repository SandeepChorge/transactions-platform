package com.madtitan94.transactionsparser.core.domain.analytics

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

/**
 * Firebase's limits, and the fact that breaking one is silent.
 *
 * Every case here is something Firebase accepts the call for and then quietly discards. The value
 * of the check is entirely in turning that silence into a failure somebody sees.
 */
class AnalyticsContractTest {

    private fun violation(
        name: String = "some_event",
        params: Map<String, Any> = emptyMap(),
        defaults: Map<String, Any> = emptyMap()
    ) = AnalyticsContract.violationOf(name, params, defaults)

    @Test
    fun `a well formed event has nothing wrong with it`() {
        assertThat(
            violation(
                name = "statement_imported",
                params = mapOf("source" to "PHONEPE", "transactionCount" to 40),
                defaults = mapOf("appName" to "Statement Sense")
            )
        ).isNull()
    }

    @Test
    fun `an event name with a space or a dash is rejected`() {
        assertThat(violation(name = "statement imported")).isNotNull()
        assertThat(violation(name = "statement-imported")).isNotNull()
    }

    @Test
    fun `an event name starting with a digit is rejected`() {
        // Firebase requires a letter first. A name like "2fa_started" is accepted by the call and
        // never appears in the console.
        assertThat(violation(name = "2fa_started")).isNotNull()
    }

    @Test
    fun `names Firebase reserves are rejected`() {
        listOf("firebase_thing", "google_thing", "ga_thing").forEach { reserved ->
            assertThat(violation(name = reserved)).isNotNull()
        }
    }

    @Test
    fun `an over-long name is rejected`() {
        assertThat(violation(name = "a".repeat(AnalyticsContract.MAX_EVENT_NAME_LENGTH + 1)))
            .isNotNull()
    }

    @Test
    fun `the parameter limit counts the defaults too`() {
        // The failure this exists for: an event with three parameters looks harmless on its own and
        // is over the line once the nine-key identity block is merged in. Whichever parameters fall
        // past the twenty-fifth are dropped without a word.
        val defaults = (1..20).associate { "default$it" to it }
        val own = (1..6).associate { "own$it" to it }

        val message = violation(params = own, defaults = defaults)

        assertThat(message).isNotNull()
        assertThat(message!!).contains("26")
    }

    @Test
    fun `exactly at the limit is allowed`() {
        val defaults = (1..20).associate { "default$it" to it }
        val own = (1..5).associate { "own$it" to it }

        assertThat(violation(params = own, defaults = defaults)).isNull()
    }

    @Test
    fun `a parameter key with a reserved prefix or bad characters is rejected`() {
        assertThat(violation(params = mapOf("firebase_id" to "x"))).isNotNull()
        assertThat(violation(params = mapOf("my key" to "x"))).isNotNull()
        assertThat(violation(params = mapOf("" to "x"))).isNotNull()
    }

    @Test
    fun `a value Firebase cannot store is rejected rather than stringified`() {
        // Left unchecked, this reaches the bundle through toString() and the console fills with
        // things like "SearchQuery(text=..., amountFromPaise=null)" — which is both useless as a
        // dimension and a way for real data to escape into analytics without anyone deciding to
        // send it.
        data class Anything(val value: String)

        val message = violation(params = mapOf("thing" to Anything("swiggy")))

        assertThat(message).isNotNull()
        assertThat(message!!).contains("Anything")
    }

    @Test
    fun `numbers and booleans are all acceptable values`() {
        assertThat(
            violation(
                params = mapOf(
                    "anInt" to 1,
                    "aLong" to 1L,
                    "aDouble" to 1.0,
                    "aFloat" to 1.0f,
                    "aBoolean" to true,
                    "aString" to "x"
                )
            )
        ).isNull()
    }

    @Test
    fun `an over-long string value is rejected`() {
        assertThat(
            violation(
                params = mapOf("long" to "x".repeat(AnalyticsContract.MAX_STRING_VALUE_LENGTH + 1))
            )
        ).isNotNull()
    }
}
