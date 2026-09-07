package com.madtitan94.transactionsparser.feature.dashboard.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

/**
 * The rules that decide what Home shows, pinned away from any screen.
 *
 * Every one of these is a rule about *missing or stale* data rather than about the happy path: a
 * saved order written by an older build, an id from a newer one, a dashboard the user deleted, an
 * account that switched everything off. Those are the states a preference file actually arrives in
 * after a few months, and none of them is reachable from the UI to test by hand.
 */
class DashboardLayoutTest {

    private fun key(id: DashboardId) = DashboardKey.BuiltIn(id)

    private val custom = CustomDashboard(
        id = "abc",
        name = "Weekends",
        widgets = listOf(DashboardWidgetId.HERO_KPI, DashboardWidgetId.TREND_CHART)
    )

    @Test
    fun `an untouched account gets the shipped dashboards in the shipped order`() {
        assertThat(DashboardLayout().all.map { it.key })
            .isEqualTo(V1_DASHBOARDS.map { it.key })
    }

    @Test
    fun `the saved order leads and anything it does not mention follows`() {
        val layout = DashboardLayout(order = listOf(key(DashboardId.PAYEES)))

        // The upgrade path, and the reason the order is stored rather than the resolved list: a
        // dashboard shipped after this order was written must appear, not stay invisible forever.
        assertThat(layout.all.first().key).isEqualTo(key(DashboardId.PAYEES))
        assertThat(layout.all.map { it.key }).containsExactly(
            key(DashboardId.PAYEES),
            key(DashboardId.PULSE),
            key(DashboardId.CATEGORIES),
            key(DashboardId.MAPPING_HEALTH)
        )
    }

    @Test
    fun `a dashboard the order names but nothing defines is dropped`() {
        // What a deleted custom dashboard leaves behind: the id stays in the saved order, because
        // rewriting three keys to tidy up one deletion is three chances to corrupt a layout.
        val layout = DashboardLayout(
            order = listOf(DashboardKey.Custom("deleted"), key(DashboardId.PULSE))
        )

        assertThat(layout.all.map { it.key }).isEqualTo(
            listOf(
                key(DashboardId.PULSE),
                key(DashboardId.CATEGORIES),
                key(DashboardId.MAPPING_HEALTH),
                key(DashboardId.PAYEES)
            )
        )
    }

    @Test
    fun `a dashboard the user built is offered alongside the shipped ones`() {
        val layout = DashboardLayout(custom = listOf(custom))

        assertThat(layout.all.last().key).isEqualTo(DashboardKey.Custom("abc"))
        assertThat(layout.all.last().name).isEqualTo("Weekends")
        assertThat(layout.all.last().widgets).hasSize(2)
    }

    @Test
    fun `switching everything off gives the whole set back rather than an empty Home`() {
        val layout = DashboardLayout(disabled = V1_DASHBOARDS.map { it.key }.toSet())

        // A Home screen with nothing on it looks broken, and there would be no way left to reach
        // the screen that undoes it.
        assertThat(layout.enabled).isNotEmpty()
        assertThat(layout.enabled.map { it.key }).isEqualTo(V1_DASHBOARDS.map { it.key })
    }

    @Test
    fun `the star only counts while the dashboard it points at is switched on`() {
        val layout = DashboardLayout(
            disabled = setOf(key(DashboardId.PAYEES)),
            defaultKey = key(DashboardId.PAYEES)
        )

        // Otherwise Home opens on a dashboard the chip row does not offer and the pager cannot
        // scroll back to.
        assertThat(layout.defaultDashboard).isEqualTo(key(DashboardId.PULSE))
    }

    @Test
    fun `with no star Home opens on the first dashboard the user can see`() {
        val layout = DashboardLayout(
            order = listOf(key(DashboardId.CATEGORIES)),
            disabled = setOf(key(DashboardId.CATEGORIES))
        )

        assertThat(layout.defaultDashboard).isEqualTo(key(DashboardId.PULSE))
    }

    @Test
    fun `a custom dashboard renders through the same widget configs as a shipped one`() {
        val definition = custom.toDefinition()

        // The whole point of the catalogue: a dashboard the user composed has no code of its own,
        // so it must arrive at the renderer in exactly the shape a shipped one does.
        assertThat(definition.widgets).isEqualTo(
            listOf(
                defaultConfigFor(DashboardWidgetId.HERO_KPI),
                defaultConfigFor(DashboardWidgetId.TREND_CHART)
            )
        )
    }

    @Test
    fun `a stored id this build does not recognise reads back as nothing`() {
        // A downgrade leaves ids from a newer version behind. Dropping one entry beats refusing to
        // open the app.
        assertThat(DashboardKey.parse("SOMETHING_NEW")).isNull()
        assertThat(DashboardKey.parse("custom:")).isNull()
        assertThat(DashboardKey.parse("PULSE")).isEqualTo(key(DashboardId.PULSE))
        assertThat(DashboardKey.parse("custom:abc")).isEqualTo(DashboardKey.Custom("abc"))
    }

    @Test
    fun `a key survives the round trip through its stored form`() {
        val keys = V1_DASHBOARDS.map { it.key } + DashboardKey.Custom("abc")

        assertThat(keys.mapNotNull { DashboardKey.parse(it.storageId) }).isEqualTo(keys)
        assertThat(keys.filter { DashboardKey.parse(it.storageId) == null }).isEmpty()
    }
}
