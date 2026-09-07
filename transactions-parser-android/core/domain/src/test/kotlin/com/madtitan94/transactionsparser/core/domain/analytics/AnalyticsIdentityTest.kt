package com.madtitan94.transactionsparser.core.domain.analytics

import assertk.assertThat
import assertk.assertions.hasLength
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

/**
 * The privacy promise, pinned.
 *
 * Every assertion here corresponds to a way the hash stops doing its job: it becomes reversible, or
 * it stops identifying one account as one account, or it invents an identity for somebody who is
 * not signed in.
 */
class AnalyticsIdentityTest {

    private val salt = "a-test-salt"

    @Test
    fun `the same account hashes to the same value every time`() {
        // Without this there are no per-user metrics at all — one person's launches would count as
        // one new user each, and the numbers would be silently, unrecoverably wrong.
        val first = AnalyticsIdentity.hash("someone@example.com", salt)
        val second = AnalyticsIdentity.hash("someone@example.com", salt)

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `the salt changes the digest`() {
        // This is what makes the digest irreversible in practice: a rainbow table built against
        // plain email digests finds nothing, because the salt was never in the exported data.
        val withSalt = AnalyticsIdentity.hash("someone@example.com", salt)
        val withAnother = AnalyticsIdentity.hash("someone@example.com", "a-different-salt")

        assertThat(withSalt).isNotEqualTo(withAnother)
    }

    @Test
    fun `the digest is not the input in any recoverable form`() {
        val hashed = AnalyticsIdentity.hash("someone@example.com", salt)!!

        // Hex only, fixed width. A digest that still carried the "@" or the domain would leak the
        // provider and narrow a brute-force search enormously.
        assertThat(hashed).hasLength(32)
        assertThat(hashed.all { it in "0123456789abcdef" }).isEqualTo(true)
    }

    @Test
    fun `case and surrounding space do not make a second account`() {
        // Email addresses arrive from Google with whatever capitalisation the user typed. Treating
        // them as distinct would split one person across two identities halfway through a funnel.
        val canonical = AnalyticsIdentity.hash("someone@example.com", salt)

        assertThat(AnalyticsIdentity.hash("Someone@Example.com", salt)).isEqualTo(canonical)
        assertThat(AnalyticsIdentity.hash("  someone@example.com  ", salt)).isEqualTo(canonical)
    }

    @Test
    fun `different accounts do not collide`() {
        assertThat(AnalyticsIdentity.hash("a@example.com", salt))
            .isNotEqualTo(AnalyticsIdentity.hash("b@example.com", salt))
    }

    @Test
    fun `nothing to hash yields null rather than a digest of nothing`() {
        // The failure this prevents: hashing "" produces a perfectly valid-looking digest, the same
        // one for every signed-out user on every device, which the console would then report as one
        // extremely busy account.
        assertThat(AnalyticsIdentity.hash(null, salt)).isNull()
        assertThat(AnalyticsIdentity.hash("", salt)).isNull()
        assertThat(AnalyticsIdentity.hash("   ", salt)).isNull()
    }

    @Test
    fun `a google subject id hashes the same way an email does`() {
        // Opaque already, but hashed anyway: it is the id Google gives this app for this person,
        // and sending it raw would let anyone holding the export join our data to someone else's.
        val subject = "104729384756019283746"

        assertThat(AnalyticsIdentity.hash(subject, salt))
            .isEqualTo(AnalyticsIdentity.hash(subject, salt))
        assertThat(AnalyticsIdentity.hash(subject, salt)).isNotEqualTo(subject)
    }
}
