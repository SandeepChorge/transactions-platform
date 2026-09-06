package com.madtitan94.transactionsparser.feature.dashboard.domain

/**
 * The widget types the dashboard renderer knows how to draw.
 *
 * Every dashboard — the four fixed ones here and any the user composes later — is nothing but an
 * ordered list of these. That is the whole point of naming them: a dashboard has no code of its own,
 * so adding one is data, and the builder a later phase adds writes the same data a fixed dashboard
 * declares.
 */
enum class DashboardWidgetId {
    /** The unmapped-money nudge: how much has no name on it, and how many payees that is. */
    INSIGHT_BANNER,

    /** The one big number at the top of a dashboard. What it measures is the config's business. */
    HERO_KPI,

    /** Money in, money out, net — three tiles. */
    TYPE_TILES,

    /** Spend over time across the range. */
    TREND_CHART,

    /** Proportion, with the total in the middle. */
    CATEGORY_DONUT,

    /** Categories ranked largest first, with a track per row. */
    CATEGORY_RANKED_LIST,

    /** Payees ranked largest first — the merged payee, never the statement name. */
    PAYEE_RANKED_LIST
}

/** What the hero number is counting. */
enum class HeroMeasure {
    /** Total spend in the range, with a comparison against the period before it. */
    NET_SPEND,

    /**
     * Share of spend that has a payee name on it, by value rather than by row count.
     *
     * By value on purpose: one unnamed rent payment matters more than twenty unnamed tea stalls, and
     * a row-count score would rank those the other way round.
     */
    MAPPED_SHARE
}

/** Whether a ranked row shows the money or the share. The layout is identical either way. */
enum class RankedValueFormat { AMOUNT, PERCENT }

/** Which payees a ranking is about. */
enum class PayeeScope {
    /** Everyone, mapped or not — unmapped names rank under whatever the statement printed. */
    ALL,

    /** Only the names with no payee yet: the work queue behind Mapping health. */
    UNMAPPED_ONLY
}

/**
 * One widget on one dashboard, with the options that widget takes.
 *
 * A sealed hierarchy rather than a single class with a bag of nullable flags: `Hero(MAPPED_SHARE)`
 * and `PayeeRanked(UNMAPPED_ONLY)` are the two places a dashboard actually differs from its
 * neighbours, and making them the type means a config that does not typecheck is a config the
 * renderer never has to defend against at runtime.
 */
sealed interface DashboardWidgetConfig {
    val id: DashboardWidgetId

    data class Hero(val measure: HeroMeasure) : DashboardWidgetConfig {
        override val id = DashboardWidgetId.HERO_KPI
    }

    data object InsightBanner : DashboardWidgetConfig {
        override val id = DashboardWidgetId.INSIGHT_BANNER
    }

    data object TypeTiles : DashboardWidgetConfig {
        override val id = DashboardWidgetId.TYPE_TILES
    }

    data object TrendChart : DashboardWidgetConfig {
        override val id = DashboardWidgetId.TREND_CHART
    }

    data object CategoryDonut : DashboardWidgetConfig {
        override val id = DashboardWidgetId.CATEGORY_DONUT
    }

    data class CategoryRanked(val value: RankedValueFormat) : DashboardWidgetConfig {
        override val id = DashboardWidgetId.CATEGORY_RANKED_LIST
    }

    data class PayeeRanked(val scope: PayeeScope) : DashboardWidgetConfig {
        override val id = DashboardWidgetId.PAYEE_RANKED_LIST
    }
}

/**
 * The dashboards that ship in v1.
 *
 * Ids only — names and subtitles are copy, and copy is translatable, so it lives in the
 * presentation module's string resources rather than here.
 */
enum class DashboardId {
    PULSE,
    CATEGORIES,
    MAPPING_HEALTH,
    PAYEES
}

data class DashboardDefinition(
    val id: DashboardId,
    val widgets: List<DashboardWidgetConfig>
)

/**
 * The four v1 dashboards, in their default order.
 *
 * Mapping health sits third rather than last because it is the one that explains the other three: if
 * a quarter of the period's spend has no name on it, every total on Pulse and Categories is a
 * partial total presented as a complete one, and the user should meet that fact before they get to
 * the payee ranking.
 */
val V1_DASHBOARDS: List<DashboardDefinition> = listOf(
    DashboardDefinition(
        id = DashboardId.PULSE,
        widgets = listOf(
            DashboardWidgetConfig.Hero(HeroMeasure.NET_SPEND),
            DashboardWidgetConfig.InsightBanner,
            DashboardWidgetConfig.TrendChart,
            DashboardWidgetConfig.CategoryRanked(RankedValueFormat.AMOUNT)
        )
    ),
    DashboardDefinition(
        id = DashboardId.CATEGORIES,
        widgets = listOf(
            DashboardWidgetConfig.CategoryDonut,
            DashboardWidgetConfig.CategoryRanked(RankedValueFormat.PERCENT),
            DashboardWidgetConfig.PayeeRanked(PayeeScope.ALL)
        )
    ),
    DashboardDefinition(
        id = DashboardId.MAPPING_HEALTH,
        widgets = listOf(
            DashboardWidgetConfig.Hero(HeroMeasure.MAPPED_SHARE),
            DashboardWidgetConfig.InsightBanner,
            DashboardWidgetConfig.PayeeRanked(PayeeScope.UNMAPPED_ONLY)
        )
    ),
    DashboardDefinition(
        id = DashboardId.PAYEES,
        widgets = listOf(
            DashboardWidgetConfig.PayeeRanked(PayeeScope.ALL),
            DashboardWidgetConfig.TypeTiles
        )
    )
)
