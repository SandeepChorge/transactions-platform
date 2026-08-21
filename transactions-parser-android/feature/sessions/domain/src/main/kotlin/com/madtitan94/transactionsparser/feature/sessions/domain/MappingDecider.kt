package com.madtitan94.transactionsparser.feature.sessions.domain

import com.madtitan94.transactionsparser.core.domain.model.Payee

/** What saving an alias should actually do, once the alias has been checked against the account. */
sealed interface MappingDecision {
    /** Attach this statement name to an existing payee, merging its current one away if it has one. */
    data class LinkTo(val payeeId: Long) : MappingDecision

    /** Create or update this name's own payee. */
    data object SaveOwn : MappingDecision

    /** The alias belongs to someone else — only the user can say whether that is the same person. */
    data class AskAboutSameName(val alias: String, val payeeId: Long) : MappingDecision
}

/**
 * The same-name rule, kept out of both mapping screens so it is decided once and tested directly.
 *
 * Session Detail and Payee Detail both let an alias be typed, and both have to answer the same
 * question: is this a second spelling of a payee that already exists, or a new one that happens to
 * share a name? Getting it wrong in either direction is a real cost — a silent duplicate payee
 * splits a history in two, and a silent merge is hard to undo.
 */
object MappingDecider {

    /**
     * @param pickedPayeeId the payee chosen from typeahead, if one was.
     * @param aliasOwner the payee already using the typed alias, if any.
     * @param currentPayeeId the payee this statement name resolves to today, if it is mapped.
     */
    fun decide(
        pickedPayeeId: Long?,
        aliasOwner: Payee?,
        currentPayeeId: Long?
    ): MappingDecision = when {
        // Picking an existing payee by name is already the answer to the question — asking again
        // would be a dialog with one sensible reply.
        pickedPayeeId != null -> MappingDecision.LinkTo(pickedPayeeId)

        // Renaming a payee to what it is already called is not a collision with anyone.
        aliasOwner == null || aliasOwner.id == currentPayeeId -> MappingDecision.SaveOwn

        else -> MappingDecision.AskAboutSameName(aliasOwner.alias, aliasOwner.id)
    }
}
