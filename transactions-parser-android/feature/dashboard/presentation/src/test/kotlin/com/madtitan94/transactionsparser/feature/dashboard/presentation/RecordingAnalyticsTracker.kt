package com.madtitan94.transactionsparser.feature.dashboard.presentation

import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsEvent
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsTracker

/**
 * Records what was tracked, in order.
 *
 * Order matters more than it looks: several of these events are only correct because of *when* they
 * are raised — logged_out before the session is cleared, mapping_completed on the transition rather
 * than on the condition — and a fake that only counted calls would pass either way.
 */
class RecordingAnalyticsTracker : AnalyticsTracker {

    val events = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
        events += event
    }

    inline fun <reified T : AnalyticsEvent> only(): List<T> = events.filterIsInstance<T>()
}
