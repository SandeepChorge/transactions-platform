package com.madtitan94.transactionsparser.feature.sessions.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.madtitan94.transactionsparser.core.domain.model.Payee
import org.junit.jupiter.api.Test

/**
 * The same-name rule decides whether a save quietly creates a twin payee, quietly merges two, or
 * stops and asks. All three are wrong in the other two situations, so each is pinned here.
 */
class MappingDeciderTest {

    @Test
    fun `a free alias is simply saved`() {
        val decision = MappingDecider.decide(
            pickedPayeeId = null,
            aliasOwner = null,
            currentPayeeId = null
        )

        assertThat(decision).isEqualTo(MappingDecision.SaveOwn)
    }

    @Test
    fun `an alias held by someone else asks before doing anything`() {
        val decision = MappingDecider.decide(
            pickedPayeeId = null,
            aliasOwner = Payee(id = 7L, alias = "Swiggy", categoryId = 1L),
            currentPayeeId = null
        )

        assertThat(decision).isEqualTo(MappingDecision.AskAboutSameName("Swiggy", 7L))
    }

    /** Editing a mapped payee's category without touching its name is not a collision. */
    @Test
    fun `a payee keeping its own alias is not asked about itself`() {
        val decision = MappingDecider.decide(
            pickedPayeeId = null,
            aliasOwner = Payee(id = 7L, alias = "Swiggy", categoryId = 1L),
            currentPayeeId = 7L
        )

        assertThat(decision).isEqualTo(MappingDecision.SaveOwn)
    }

    @Test
    fun `picking an existing payee links straight to it`() {
        val decision = MappingDecider.decide(
            pickedPayeeId = 9L,
            aliasOwner = null,
            currentPayeeId = null
        )

        assertThat(decision).isEqualTo(MappingDecision.LinkTo(9L))
    }

    /**
     * Picking from typeahead is already an answer to the question the prompt would ask, so it
     * wins over the collision it necessarily creates — otherwise every pick raises a dialog.
     */
    @Test
    fun `a pick is not second-guessed by the collision its own alias causes`() {
        val decision = MappingDecider.decide(
            pickedPayeeId = 9L,
            aliasOwner = Payee(id = 9L, alias = "Swiggy", categoryId = 1L),
            currentPayeeId = 3L
        )

        assertThat(decision).isEqualTo(MappingDecision.LinkTo(9L))
    }
}
