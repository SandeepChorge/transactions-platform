package com.madtitan94.transactionsparser.core.designsystem.charts

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.madtitan94.transactionsparser.core.designsystem.theme.DarkAppColors
import com.madtitan94.transactionsparser.core.designsystem.theme.LightAppColors
import org.junit.jupiter.api.Test

/**
 * Keeps the slot arithmetic and the palette from drifting apart.
 *
 * [CHART_SLOT_COUNT] is what `buildCategorySlices` counts in, and `AppColors.chartSeries` is what
 * gets drawn. If someone adds an eighth colour to the palette without touching the constant, that
 * colour is unreachable and nothing else fails; if they remove one, the slot arithmetic starts
 * pointing past the end of the list. Neither shows up in a build.
 */
class ChartPaletteTest {

    @Test
    fun `both themes carry exactly the slots the slot arithmetic assigns`() {
        assertThat(LightAppColors.chartSeries.size).isEqualTo(CHART_SLOT_COUNT)
        assertThat(DarkAppColors.chartSeries.size).isEqualTo(CHART_SLOT_COUNT)
    }

    @Test
    fun `no colour is repeated within a theme`() {
        assertThat(LightAppColors.chartSeries.toSet().size).isEqualTo(CHART_SLOT_COUNT)
        assertThat(DarkAppColors.chartSeries.toSet().size).isEqualTo(CHART_SLOT_COUNT)
    }
}
