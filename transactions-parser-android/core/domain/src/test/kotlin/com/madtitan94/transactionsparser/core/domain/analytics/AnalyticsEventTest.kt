package com.madtitan94.transactionsparser.core.domain.analytics

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

/**
 * Every event this app can send, checked against Firebase's rules before it is ever sent once.
 *
 * The parameter budget is the reason this file exists. Nine of the twenty-five slots are spent on
 * the identity and build block that rides along with everything, and an event that overruns the
 * remainder does not fail — it arrives with its last parameters missing, which looks exactly like a
 * feature nobody uses.
 */
class AnalyticsEventTest {

    /**
     * A stand-in for the real default block, at its full size: identity present, which is the worst
     * case. Checking against a smaller map would pass an event that breaks the moment a user signs
     * in, which is the only state anyone's data is collected in.
     */
    private val worstCaseDefaults: Map<String, Any> = mapOf(
        AnalyticsParams.ACCOUNT_HASH to "0".repeat(32),
        AnalyticsParams.EMAIL_HASH to "0".repeat(32),
        AnalyticsParams.APP_NAME to "Statement Sense",
        AnalyticsParams.APP_VERSION to "1.0.0",
        AnalyticsParams.DEVICE to "tangorpro",
        AnalyticsParams.MANUFACTURER to "Google",
        AnalyticsParams.MODEL to "Pixel Tablet",
        AnalyticsParams.OS_VERSION to 36,
        AnalyticsParams.OS_RELEASE to "16"
    )

    /**
     * One instance of every event, with values at the size they would really arrive at.
     *
     * [everyEventIsRepresentedHere] is what stops this list going stale — a new event that is not
     * added here fails that test rather than quietly skipping every check below.
     */
    private val allEvents: List<AnalyticsEvent> = listOf(
        AnalyticsEvent.AppStarted,
        AnalyticsEvent.StatementImported(source = "PHONEPE", transactionCount = 411, succeeded = true),
        AnalyticsEvent.StatementImported(
            source = AnalyticsParams.SOURCE_UNKNOWN,
            transactionCount = 0,
            succeeded = false,
            failureReason = "UNRECOGNIZED_FORMAT"
        ),
        AnalyticsEvent.MappingCompleted(payeeCount = 89, transactionCount = 411),
        AnalyticsEvent.MappingCancelled(mappedCount = 12, unmappedCount = 399),
        AnalyticsEvent.DefaultDashboardChanged(dashboard = "PULSE"),
        AnalyticsEvent.DataExported(format = AnalyticsParams.FORMAT_CSV, rowCount = 1143),
        AnalyticsEvent.LoggedIn,
        AnalyticsEvent.LoggedOut
    )

    @Test
    fun `every event is inside Firebase's limits with the identity block attached`() {
        allEvents.forEach { event ->
            val violation =
                AnalyticsContract.violationOf(event.name, event.params, worstCaseDefaults)
            assertThat(violation, name = "${event.name}: $violation").isNull()
        }
    }

    @Test
    fun `every event is represented here`() {
        // Reflection rather than a hand-kept count: the list above is only a guard if forgetting to
        // extend it is itself a failure.
        val declared = AnalyticsEvent::class.sealedSubclasses.mapNotNull { it.simpleName }.toSet()
        val covered = allEvents.map { it::class.simpleName }.toSet()

        assertThat(declared - covered).isEmpty()
    }

    @Test
    fun `event names are the ones dashboards will be built on`() {
        // Renaming a shipped event splits its history in two with no way to stitch it back, so the
        // names are pinned here deliberately: changing one should require changing this test, and
        // noticing why.
        assertThat(AnalyticsEvent.AppStarted.name).isEqualTo("app_started")
        assertThat(AnalyticsEvent.LoggedIn.name).isEqualTo("logged_in")
        assertThat(AnalyticsEvent.LoggedOut.name).isEqualTo("logged_out")
        assertThat(AnalyticsEvent.MappingCompleted(1, 1).name).isEqualTo("mapping_completed")
        assertThat(AnalyticsEvent.MappingCancelled(1, 1).name).isEqualTo("mapping_cancelled")
        assertThat(AnalyticsEvent.DefaultDashboardChanged("PULSE").name)
            .isEqualTo("default_dashboard_changed")
        assertThat(AnalyticsEvent.DataExported("csv", 1).name).isEqualTo("data_exported")
        assertThat(AnalyticsEvent.StatementImported("PHONEPE", 1, true).name)
            .isEqualTo("statement_imported")
    }

    @Test
    fun `a failure reason rides along only when there is one`() {
        // Absent rather than "none": an event that always carries the key makes every successful
        // import look like it has a reason, and the console cannot filter on the difference.
        val succeeded = AnalyticsEvent.StatementImported("PHONEPE", 411, succeeded = true)
        val failed = AnalyticsEvent.StatementImported(
            AnalyticsParams.SOURCE_UNKNOWN, 0, succeeded = false, failureReason = "NOT_A_PDF"
        )

        assertThat(succeeded.params[AnalyticsParams.FAILURE_REASON]).isNull()
        assertThat(failed.params[AnalyticsParams.FAILURE_REASON]).isNotNull()
    }

    @Test
    fun `no event carries free text`() {
        // The privacy rule, as a test rather than a comment. Every string an event sends must be
        // something from a fixed set — an enum name, a format, a hash — so that a payee name or a
        // file name cannot reach analytics even by a careless change to a call site.
        val allowed = setOf(
            "PHONEPE", "GOOGLE_PAY", AnalyticsParams.SOURCE_UNKNOWN,
            "UNRECOGNIZED_FORMAT", "NOT_A_PDF",
            "PULSE", AnalyticsParams.DASHBOARD_CUSTOM,
            AnalyticsParams.FORMAT_CSV, AnalyticsParams.FORMAT_BACKUP
        )

        val strings = allEvents
            .flatMap { it.params.values }
            .filterIsInstance<String>()

        assertThat(strings.filterNot { it in allowed }).isEmpty()
    }
}
