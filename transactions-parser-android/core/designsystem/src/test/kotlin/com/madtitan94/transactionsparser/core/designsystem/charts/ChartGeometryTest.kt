package com.madtitan94.transactionsparser.core.designsystem.charts

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

/**
 * The arithmetic the `Canvas` blocks depend on.
 *
 * Every case here is one a real month can produce — a period with nothing in it, a single category,
 * a bucket of zero — and each of them is a division waiting to happen.
 */
class ChartGeometryTest {

    private val tolerance = 0.01f

    // ---- donut -------------------------------------------------------------------------------

    @Test
    fun `slices and gaps together close the ring`() {
        val arcs = donutArcs(listOf(50L, 30L, 20L), gapDegrees = 2f)

        assertThat(arcs).hasSize(3)
        val sweeps = arcs.sumOf { it.sweepDegrees.toDouble() }
        assertThat(sweeps + 3 * 2.0).isBetween(360.0 - tolerance, 360.0 + tolerance)
    }

    @Test
    fun `the first slice starts at twelve o'clock`() {
        assertThat(donutArcs(listOf(1L), gapDegrees = 2f).single().startDegrees)
            .isEqualTo(DONUT_START_DEGREES)
    }

    @Test
    fun `a single slice gets no gap so the ring is not notched`() {
        val only = donutArcs(listOf(500L), gapDegrees = 2f).single()
        assertThat(only.sweepDegrees).isBetween(360f - tolerance, 360f + tolerance)
    }

    @Test
    fun `an empty period draws no arcs`() {
        assertThat(donutArcs(emptyList(), gapDegrees = 2f)).isEmpty()
        assertThat(donutArcs(listOf(0L, 0L), gapDegrees = 2f)).isEmpty()
    }

    @Test
    fun `gaps wider than the circle are dropped rather than inverting the slices`() {
        val arcs = donutArcs(List(10) { 1L }, gapDegrees = 40f)

        assertThat(arcs.all { it.sweepDegrees > 0f }).isEqualTo(true)
        assertThat(arcs.sumOf { it.sweepDegrees.toDouble() })
            .isBetween(360.0 - tolerance, 360.0 + tolerance)
    }

    @Test
    fun `slices follow each other with the gap between them`() {
        val arcs = donutArcs(listOf(1L, 1L), gapDegrees = 4f)

        val expectedSecondStart = arcs[0].startDegrees + arcs[0].sweepDegrees + 4f
        assertThat(arcs[1].startDegrees).isBetween(
            expectedSecondStart - tolerance,
            expectedSecondStart + tolerance
        )
    }

    // ---- trend line --------------------------------------------------------------------------

    @Test
    fun `the trend is measured from zero, not from the quietest day`() {
        // Against a floating baseline these would be 0 and 1; against zero the second is twice the
        // first, which is what the shape is supposed to say.
        val fractions = trendFractions(listOf(500L, 1000L))

        assertThat(fractions[0]).isBetween(0.5f - tolerance, 0.5f + tolerance)
        assertThat(fractions[1]).isEqualTo(1f)
    }

    @Test
    fun `a period of all zeroes is a flat line on the baseline`() {
        assertThat(trendFractions(listOf(0L, 0L, 0L))).containsExactly(0f, 0f, 0f)
    }

    @Test
    fun `an empty period has nothing to plot`() {
        assertThat(trendFractions(emptyList())).isEmpty()
    }

    // ---- paired bars -------------------------------------------------------------------------

    @Test
    fun `both series are scaled against the largest value across the two`() {
        val fractions = pairedBarFractions(
            inValues = listOf(500L, 1_000L),
            outValues = listOf(2_000L, 1_000L)
        )

        assertThat(fractions[0].first).isBetween(0.25f - tolerance, 0.25f + tolerance)
        assertThat(fractions[0].second).isEqualTo(1f)
        assertThat(fractions[1].first).isBetween(0.5f - tolerance, 0.5f + tolerance)
    }

    @Test
    fun `mismatched series lengths fall back to the shorter one`() {
        assertThat(pairedBarFractions(listOf(1L), listOf(1L, 2L, 3L))).hasSize(1)
    }

    @Test
    fun `a period with no movement gives zero-height bars rather than a division by zero`() {
        assertThat(pairedBarFractions(listOf(0L, 0L), listOf(0L, 0L)))
            .containsExactly(0f to 0f, 0f to 0f)
    }

    // ---- ranked track ------------------------------------------------------------------------

    @Test
    fun `a ranked track is filled against the top row, so the top row is always full`() {
        assertThat(trackFraction(10_960_00L, 10_960_00L)).isEqualTo(1f)
        assertThat(trackFraction(5_480_00L, 10_960_00L)).isBetween(0.5f - tolerance, 0.5f + tolerance)
    }

    @Test
    fun `an empty ranking leaves the track empty rather than undefined`() {
        assertThat(trackFraction(0L, 0L)).isEqualTo(0f)
        assertThat(trackFraction(100L, 0L)).isEqualTo(0f)
    }
}
