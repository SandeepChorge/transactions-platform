package com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madtitan94.transactionsparser.core.designsystem.charts.ChartSlice
import com.madtitan94.transactionsparser.core.designsystem.charts.StackedShareBar
import com.madtitan94.transactionsparser.core.designsystem.charts.AppChartDimens
import com.madtitan94.transactionsparser.core.designsystem.charts.ChartTrack
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.feature.dashboard.presentation.R

/**
 * The one number at the top of Pulse: what left the account in this period.
 *
 * The delta line underneath is dropped rather than zeroed whenever it cannot be computed — no
 * previous period, or a previous period of nothing. "+0%" against an empty baseline is not a
 * smaller claim than a real comparison, it is a false one.
 */
@Composable
fun NetSpendHero(
    rangeLabel: String,
    totalPaise: Long,
    deltaPercent: Int?,
    biggestMover: String?,
    payeeCount: Int,
    slices: List<ChartSlice>,
    isEmpty: Boolean,
    modifier: Modifier = Modifier
) {
    DashboardCard(modifier = modifier) {
        CardEyebrow(stringResource(R.string.dash_net_spend_label, rangeLabel))

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            HeroNumber(formatPaise(totalPaise))
            when {
                isEmpty -> Text(
                    text = stringResource(R.string.dash_hero_empty),
                    style = AppTypography.body,
                    color = AppTheme.colors.textSecondary
                )

                deltaPercent == null -> Text(
                    text = stringResource(R.string.dash_hero_count, payeeCount),
                    style = AppTypography.body,
                    color = AppTheme.colors.textSecondary
                )

                else -> DeltaLine(deltaPercent = deltaPercent, biggestMover = biggestMover)
            }
        }

        // The share bar repeats the category split as a single strip. It is the artboard's own
        // answer to "what is this number made of" without spending a second card on it.
        if (slices.isNotEmpty()) {
            StackedShareBar(slices = slices, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The one big number on a hero card, shrunk to fit rather than wrapped.
 *
 * `DashboardSpec` §5 forbids abbreviating a hero number, so a total too wide for the card cannot
 * become "₹2.1L" here the way a KPI tile's would. Left to wrap it breaks mid-number instead — a
 * 360dp phone rendered ₹2,09,394.93 as "₹2,09,394.9" above a lone "3", which reads as two numbers
 * and is worse than either alternative. Auto-sizing keeps every digit on one line and gives up type
 * size, which is the one thing a hero can afford to lose.
 *
 * The floor is 24sp: below that the number stops out-weighing the sentence under it, and at 24sp a
 * 360dp card still fits eighteen mono digits — more than a rupee total will ever have.
 */
@Composable
private fun HeroNumber(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = AppTypography.hero.copy(color = AppTheme.colors.textPrimary),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 24.sp,
            maxFontSize = AppTypography.hero.fontSize,
            stepSize = 1.sp
        )
    )
}

/**
 * "↑ 12% more than the period before, mostly Groceries".
 *
 * Up-spend is coloured as the negative outcome and down-spend as the positive one, per
 * `DashboardSpec` §5: the colour carries the meaning, not the raw sign. A period exactly level with
 * the one before gets a sentence of its own rather than "↑ 0% more", which reads as a rounding bug.
 */
@Composable
private fun DeltaLine(deltaPercent: Int, biggestMover: String?) {
    val magnitude = kotlin.math.abs(deltaPercent)
    val headline = when {
        deltaPercent == 0 -> stringResource(R.string.dash_delta_flat)
        deltaPercent > 0 -> stringResource(R.string.dash_delta_more, magnitude)
        else -> stringResource(R.string.dash_delta_less, magnitude)
    }
    val tail = when {
        deltaPercent == 0 -> null
        biggestMover != null -> stringResource(R.string.dash_delta_because, biggestMover)
        else -> stringResource(R.string.dash_delta_than_before)
    }

    // One line, two inks: the change itself carries the colour, the clause explaining it stays
    // muted. Two stacked Texts would break the baseline the design puts them on, and colouring the
    // whole sentence would turn a routine month into an alarm.
    val text = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = if (deltaPercent > 0) AppTheme.colors.danger else AppTheme.colors.success,
                fontWeight = FontWeight.SemiBold
            )
        ) {
            append(headline)
        }
        if (tail != null) {
            append(" ")
            withStyle(SpanStyle(color = AppTheme.colors.textSecondary)) { append(tail) }
        }
    }

    Text(text = text, style = AppTypography.body, color = AppTheme.colors.textSecondary)
}

/**
 * Mapping health's hero: what share of the period's spend has a payee name on it.
 *
 * Scored by value rather than by row count, which is the whole reason this dashboard exists — one
 * unnamed rent payment is worth more attention than twenty unnamed tea stalls, and a row-count score
 * would rank them the other way round.
 *
 * The card stays at 100% rather than disappearing. A user who has just finished naming everything
 * should see that they finished; a card that vanishes on success looks like a card that crashed.
 */
@Composable
fun MappedShareHero(
    rangeLabel: String,
    mappedPercent: Int,
    totalPaise: Long,
    modifier: Modifier = Modifier
) {
    DashboardCard(modifier = modifier) {
        CardEyebrow(stringResource(R.string.dash_mapped_label, rangeLabel))

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            HeroNumber("$mappedPercent%")
            Text(
                text = stringResource(R.string.dash_mapped_of, formatPaise(totalPaise)),
                style = AppTypography.body,
                color = AppTheme.colors.textSecondary
            )
        }

        ChartTrack(
            fraction = mappedPercent / 100f,
            height = AppChartDimens.trackMapping,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = if (mappedPercent >= 100) {
                stringResource(R.string.dash_mapped_all)
            } else {
                stringResource(R.string.dash_mapped_note)
            },
            style = AppTypography.body,
            color = AppTheme.colors.textSecondary
        )
    }
}
