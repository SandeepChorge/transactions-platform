package com.madtitan94.transactionsparser.feature.sessions.domain

import com.madtitan94.transactionsparser.core.domain.model.Payee

/**
 * Offers existing payees while an alias is being typed, so a second spelling of a shop is linked
 * to the payee already there instead of quietly becoming a second one.
 *
 * Substring rather than prefix matching: statement names bury the useful word in the middle
 * ("PAYTM SWIGGY BANGALORE"), so a user typing "swig" is looking for a payee whose alias they only
 * half remember. No new library — a case-insensitive `contains` over an account's payees is a list
 * measured in hundreds, evaluated once per keystroke.
 */
object AliasSuggester {

    /** Enough to be useful, few enough not to bury the text field being typed into. */
    const val DEFAULT_LIMIT = 5

    /**
     * Payees whose alias contains [typed], best match first: aliases that start with what was
     * typed come before ones that merely contain it, and the exact match — the one the
     * same-name prompt would fire on — comes first of all.
     *
     * Blank input suggests nothing. A dropdown that opens with every payee in the account the
     * moment the field is focused is noise, not help.
     */
    fun suggest(
        payees: List<Payee>,
        typed: String,
        limit: Int = DEFAULT_LIMIT
    ): List<Payee> {
        val query = typed.trim()
        if (query.isBlank()) return emptyList()

        return payees
            .filter { it.alias.contains(query, ignoreCase = true) }
            .sortedWith(
                compareBy(
                    { !it.alias.equals(query, ignoreCase = true) },
                    { !it.alias.startsWith(query, ignoreCase = true) },
                    { it.alias.lowercase() }
                )
            )
            .take(limit)
    }
}
