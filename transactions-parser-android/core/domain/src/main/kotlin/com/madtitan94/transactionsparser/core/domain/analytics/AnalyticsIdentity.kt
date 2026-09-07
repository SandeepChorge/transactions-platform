package com.madtitan94.transactionsparser.core.domain.analytics

import java.security.MessageDigest
import java.util.Locale

/**
 * Turns a signed-in user into an identifier that is stable, comparable, and useless to anyone who
 * gets hold of the analytics export.
 *
 * ### Why salted SHA-256 rather than MD5
 *
 * Hashing an email at all is only worth doing if the digest cannot be turned back into the email.
 * An unsalted digest of an email — MD5 or SHA-256 alike — fails that: the input space is small and
 * predictable enough that a commodity rainbow table reverses it in seconds, and anyone holding a
 * list of candidate addresses can confirm a match by hashing them. The salt is what makes the
 * digest irreversible in practice, because it is not present in the exported data.
 *
 * The salt therefore has to stay out of the analytics payload *and* out of this repository, which
 * is public. It arrives as a build-time value; see `:app`'s `ANALYTICS_SALT`.
 *
 * ### Stability
 *
 * The same account must hash to the same value across launches, reinstalls and devices, or every
 * per-user metric counts one person as many. That rules out a random per-install id, and it is why
 * the input is normalised — trimmed and lower-cased — before hashing: `A@b.com` and `a@b.com` are
 * one account, and would otherwise be two rows.
 */
object AnalyticsIdentity {

    /**
     * Hex characters kept from the digest. 32 is 128 bits — far past any collision concern at this
     * app's scale, comfortably inside Firebase's value limits, and the same length as the MD5
     * digests this replaces, so nothing downstream has to care that the algorithm changed.
     */
    private const val DIGEST_LENGTH = 32

    /**
     * The salted digest of [value], or `null` when there is nothing to hash.
     *
     * Null rather than an empty string on purpose. An empty string is a *value*: it becomes a real
     * bucket in the console that silently merges every signed-out user into one phantom account.
     * A null is dropped from the parameter map, and the dimension is honestly absent.
     */
    fun hash(value: String?, salt: String): String? {
        val normalised = value?.trim()?.lowercase(Locale.US)
        if (normalised.isNullOrEmpty()) return null

        val digest = MessageDigest.getInstance("SHA-256")
        // Salt first, then the value: prefixing means an attacker cannot precompute anything about
        // the value without the salt, which is the entire point of having one.
        digest.update(salt.toByteArray(Charsets.UTF_8))
        digest.update(normalised.toByteArray(Charsets.UTF_8))

        return digest.digest()
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
            .take(DIGEST_LENGTH)
    }
}
