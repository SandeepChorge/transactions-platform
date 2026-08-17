package com.madtitan94.transactionsparser.feature.auth.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * The `sub` claim becomes every row's `ownerId`, so a wrong or missing value here either splits
 * one person's data in two or hands it to the wrong account.
 */
class GoogleSubjectTest {

    private fun idToken(payloadJson: String): String {
        val encode = { s: String ->
            Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
        }
        return "${encode("""{"alg":"RS256"}""")}.${encode(payloadJson)}.fake-signature"
    }

    @Test
    fun `reads the sub claim rather than the email`() {
        val token = idToken(
            """{"iss":"https://accounts.google.com","sub":"109384756102938475610",""" +
                """"email":"someone@gmail.com","name":"Someone"}"""
        )

        assertThat(googleSubjectOf(token)).isEqualTo("109384756102938475610")
    }

    @Test
    fun `unpadded base64url payloads decode, which is how real tokens arrive`() {
        // Length chosen so the encoding would need padding; real Google tokens omit it.
        val token = idToken("""{"sub":"12345","a":"bc"}""")

        assertThat(googleSubjectOf(token)).isEqualTo("12345")
    }

    @Test
    fun `a payload with no sub yields null rather than a wrong owner id`() {
        val token = idToken("""{"email":"someone@gmail.com"}""")

        assertThat(googleSubjectOf(token)).isNull()
    }

    @Test
    fun `an empty sub is treated as missing`() {
        assertThat(googleSubjectOf(idToken("""{"sub":""}"""))).isNull()
    }

    @Test
    fun `a malformed token yields null instead of throwing`() {
        assertThat(googleSubjectOf("not-a-jwt")).isNull()
        assertThat(googleSubjectOf("")).isNull()
        assertThat(googleSubjectOf("header..signature")).isNull()
        assertThat(googleSubjectOf("header.!!!not-base64!!!.signature")).isNull()
    }

    @Test
    fun `a payload that decodes but is not json yields null`() {
        val notJson = Base64.getUrlEncoder().withoutPadding().encodeToString("plain text".toByteArray())

        assertThat(googleSubjectOf("header.$notJson.signature")).isNull()
    }
}
