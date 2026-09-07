package com.madtitan94.transactionsparser.core.domain.analytics

/**
 * Firebase's own limits, checked before anything is sent rather than discovered afterwards.
 *
 * Firebase does not reject a malformed event loudly. It drops it, or truncates it, or silently
 * ignores the parameters past the twenty-fifth — and the only symptom is a dimension that is
 * mysteriously empty in a console nobody looks at for a month. Checking here turns all of that into
 * a failure at the call site, in a unit test, on the developer's machine.
 *
 * Pure Kotlin on purpose: these rules are the part worth testing, and none of them needs Android or
 * a Firebase instance to be true.
 */
object AnalyticsContract {

    const val MAX_PARAMS = 25
    const val MAX_KEY_LENGTH = 40
    const val MAX_EVENT_NAME_LENGTH = 40
    const val MAX_STRING_VALUE_LENGTH = 100

    private val NAME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")

    /** Prefixes Firebase reserves for itself. An event using one is dropped without complaint. */
    private val RESERVED_PREFIXES = listOf("firebase_", "google_", "ga_")

    /**
     * What is wrong with an event, or `null` when nothing is.
     *
     * A returned string rather than a thrown exception, so the caller decides the consequence —
     * which differs by build type, and that difference matters. See `FirebaseAnalyticsTracker`.
     */
    fun violationOf(
        eventName: String,
        params: Map<String, Any>,
        defaultParams: Map<String, Any>
    ): String? {
        validateName(eventName)?.let { return "Event name '$eventName': $it" }

        // Firebase's limit of 25 covers the defaults and the event's own parameters together, so an
        // event that is fine on its own can still be over the line once the identity block is
        // merged in. Counting them separately is how the last parameters go missing.
        val total = params.size + defaultParams.size
        if (total > MAX_PARAMS) {
            return "Event '$eventName' has $total parameters " +
                "(${defaultParams.size} default + ${params.size} own), over Firebase's $MAX_PARAMS."
        }

        params.forEach { (key, value) ->
            validateName(key)?.let { return "Event '$eventName' parameter '$key': $it" }
            validateValue(value)?.let { return "Event '$eventName' parameter '$key': $it" }
        }
        return null
    }

    private fun validateName(name: String): String? = when {
        name.isBlank() -> "must not be blank."
        name.length > MAX_EVENT_NAME_LENGTH -> "exceeds the $MAX_EVENT_NAME_LENGTH character limit."
        !name.matches(NAME_PATTERN) ->
            "must start with a letter and contain only letters, digits and underscores."
        RESERVED_PREFIXES.any { name.startsWith(it) } ->
            "uses a prefix Firebase reserves (${RESERVED_PREFIXES.joinToString()})."
        else -> null
    }

    /**
     * Only the types a Firebase bundle can actually carry.
     *
     * Anything else reaches the bundle through `toString()`, which is how a data class ends up in
     * the console as its own source code and a `Long` timestamp becomes an unqueryable string.
     */
    private fun validateValue(value: Any): String? = when (value) {
        is String ->
            if (value.length > MAX_STRING_VALUE_LENGTH) {
                "is longer than the $MAX_STRING_VALUE_LENGTH character value limit."
            } else {
                null
            }

        is Int, is Long, is Short, is Byte, is Double, is Float, is Boolean -> null
        else -> "is a ${value::class.simpleName}, which Firebase cannot store " +
            "(use a String, a number or a Boolean)."
    }
}
