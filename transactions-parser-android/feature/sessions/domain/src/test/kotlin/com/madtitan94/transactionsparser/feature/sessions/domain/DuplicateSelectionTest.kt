package com.madtitan94.transactionsparser.feature.sessions.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * The label a payee card shows comes straight from this, so an off-by-one at either boundary
 * would tell the user their money is counted when it isn't, or the reverse.
 */
class DuplicateSelectionTest {

    @Test
    fun `no duplicates at all is NONE, not a special case`() {
        assertThat(DuplicateSelection.of(total = 0, excluded = 0)).isEqualTo(DuplicateSelection.NONE)
    }

    @Test
    fun `none excluded is NONE`() {
        assertThat(DuplicateSelection.of(total = 5, excluded = 0)).isEqualTo(DuplicateSelection.NONE)
    }

    @Test
    fun `all excluded is ALL — what a fresh import produces`() {
        assertThat(DuplicateSelection.of(total = 5, excluded = 5)).isEqualTo(DuplicateSelection.ALL)
    }

    @Test
    fun `one short of all is still SOME`() {
        assertThat(DuplicateSelection.of(total = 5, excluded = 4)).isEqualTo(DuplicateSelection.SOME)
    }

    @Test
    fun `one excluded out of many is SOME`() {
        assertThat(DuplicateSelection.of(total = 5, excluded = 1)).isEqualTo(DuplicateSelection.SOME)
    }

    @Test
    fun `a single duplicate is only ever NONE or ALL`() {
        assertThat(DuplicateSelection.of(total = 1, excluded = 0)).isEqualTo(DuplicateSelection.NONE)
        assertThat(DuplicateSelection.of(total = 1, excluded = 1)).isEqualTo(DuplicateSelection.ALL)
    }
}
