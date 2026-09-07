package com.madtitan94.transactionsparser.feature.dashboard.presentation.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardWidgetId

/** The size of one card in the preview strip. Proportioned like a card, not like a thumbnail. */
private val TILE_WIDTH = 88.dp
private val TILE_HEIGHT = 62.dp

/**
 * A card drawn as shapes rather than data — what it looks like, not what it says.
 *
 * The builder is deciding composition and order, and it decides them before the dashboard exists, so
 * there are no figures to preview. Drawing the silhouette answers the question the strip is actually
 * for — is the big number at the top, is the chart above or below the list — without implying the
 * numbers underneath are real. It also costs no query, which is why the strip can update on every
 * keystroke and every drag.
 */
@Composable
fun WidgetSilhouette(widget: DashboardWidgetId, modifier: Modifier = Modifier) {
    val ink = AppTheme.colors.accentGraphic
    val muted = AppTheme.colors.borderSubtle

    Box(
        modifier = modifier
            .width(TILE_WIDTH)
            .height(TILE_HEIGHT)
            .clip(AppShapes.chip)
            .background(AppTheme.colors.surfaceAlt)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        when (widget) {
            DashboardWidgetId.HERO_KPI -> Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Bar(width = 52.dp, height = 12.dp, color = ink)
                Bar(width = 34.dp, height = 4.dp, color = muted)
            }

            DashboardWidgetId.INSIGHT_BANNER -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(AppShapes.bar)
                    .background(AppTheme.colors.accentSurface)
            )

            DashboardWidgetId.TYPE_TILES -> Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(AppShapes.bar)
                            .background(if (it == 1) ink else muted)
                    )
                }
            }

            DashboardWidgetId.TREND_CHART -> Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val points = listOf(0.7f, 0.25f, 0.55f, 0.1f, 0.45f, 0.0f)
                val step = size.width / (points.size - 1)
                points.forEachIndexed { index, fraction ->
                    if (index == points.lastIndex) return@forEachIndexed
                    drawLine(
                        color = ink,
                        start = Offset(index * step, size.height * fraction),
                        end = Offset((index + 1) * step, size.height * points[index + 1]),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            DashboardWidgetId.CATEGORY_DONUT -> Canvas(Modifier.size(34.dp)) {
                drawCircle(
                    color = muted,
                    radius = size.minDimension / 2 - 3.dp.toPx(),
                    style = Stroke(width = 6.dp.toPx())
                )
                drawArc(
                    color = ink,
                    startAngle = -90f,
                    sweepAngle = 220f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx())
                )
            }

            DashboardWidgetId.CATEGORY_RANKED_LIST -> Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(52.dp, 38.dp, 24.dp).forEachIndexed { index, width ->
                    Bar(width = width, height = 5.dp, color = if (index == 0) ink else muted)
                }
            }

            DashboardWidgetId.PAYEE_RANKED_LIST -> Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(46.dp, 34.dp, 22.dp).forEachIndexed { index, width ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(AppShapes.avatar)
                                .background(if (index == 0) ink else muted)
                        )
                        Bar(width = width, height = 5.dp, color = muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, color: Color) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(AppShapes.bar)
            .background(color)
    )
}
