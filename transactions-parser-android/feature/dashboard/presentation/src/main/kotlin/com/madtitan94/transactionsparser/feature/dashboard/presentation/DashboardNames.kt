package com.madtitan94.transactionsparser.feature.dashboard.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardDefinition
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardId
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardKey
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardWidgetId

/**
 * What a dashboard is called on screen.
 *
 * Two sources, deliberately: the four that ship are translatable copy and live in string resources,
 * while a dashboard the user built carries the name they typed and must be shown back verbatim. A
 * custom dashboard with a blank name cannot be saved, so the fallback below is unreachable in
 * practice and exists only so this returns a String rather than a String?.
 */
@Composable
fun dashboardName(definition: DashboardDefinition): String = when (val key = definition.key) {
    is DashboardKey.BuiltIn -> stringResource(dashboardNameRes(key.id))
    is DashboardKey.Custom -> definition.name.orEmpty()
        .ifBlank { stringResource(R.string.dash_untitled) }
}

fun dashboardNameRes(id: DashboardId): Int = when (id) {
    DashboardId.PULSE -> R.string.dash_pulse
    DashboardId.CATEGORIES -> R.string.dash_categories
    DashboardId.MAPPING_HEALTH -> R.string.dash_mapping_health
    DashboardId.PAYEES -> R.string.dash_payees
}

/** The builder's label for a card type. */
fun widgetNameRes(id: DashboardWidgetId): Int = when (id) {
    DashboardWidgetId.INSIGHT_BANNER -> R.string.widget_insight_banner
    DashboardWidgetId.HERO_KPI -> R.string.widget_hero
    DashboardWidgetId.TYPE_TILES -> R.string.widget_type_tiles
    DashboardWidgetId.TREND_CHART -> R.string.widget_trend
    DashboardWidgetId.CATEGORY_DONUT -> R.string.widget_donut
    DashboardWidgetId.CATEGORY_RANKED_LIST -> R.string.widget_category_ranked
    DashboardWidgetId.PAYEE_RANKED_LIST -> R.string.widget_payee_ranked
}

/** One line saying what the card answers, so the checklist is readable without prior knowledge. */
fun widgetDescriptionRes(id: DashboardWidgetId): Int = when (id) {
    DashboardWidgetId.INSIGHT_BANNER -> R.string.widget_insight_banner_sub
    DashboardWidgetId.HERO_KPI -> R.string.widget_hero_sub
    DashboardWidgetId.TYPE_TILES -> R.string.widget_type_tiles_sub
    DashboardWidgetId.TREND_CHART -> R.string.widget_trend_sub
    DashboardWidgetId.CATEGORY_DONUT -> R.string.widget_donut_sub
    DashboardWidgetId.CATEGORY_RANKED_LIST -> R.string.widget_category_ranked_sub
    DashboardWidgetId.PAYEE_RANKED_LIST -> R.string.widget_payee_ranked_sub
}
