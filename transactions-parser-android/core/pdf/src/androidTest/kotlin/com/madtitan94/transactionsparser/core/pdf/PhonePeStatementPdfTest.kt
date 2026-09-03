package com.madtitan94.transactionsparser.core.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedStatement
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedTransaction
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.core.parsing.PhonePeStatementParser
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Reads a real PDF file with the real extractor and hands the result to the real parser.
 *
 * Everything else that covers this parser feeds it a text string, which quietly assumes the
 * assumption under test: that PdfBox lays a statement page out the way whoever wrote the fixture
 * expected. A real June 2026 statement lost 5 of its 142 transactions while every one of those
 * tests passed. Verifying against a *different* PDF reader is no better — a check run with the
 * `pdftotext` CLI reported a broken row that does not exist under `pdfbox-android`, the library
 * the app actually ships.
 *
 * So this suite exercises the shipping path end to end: an asset PDF, [PdfBoxStatementTextExtractor]
 * with its production `sortByPosition` setting, then [PhonePeStatementParser]. It is the only test
 * here that can catch a regression in how the two fit together.
 */
@RunWith(AndroidJUnit4::class)
class PhonePeStatementPdfTest {

    private lateinit var extractedText: String
    private lateinit var statement: ParsedStatement

    @Before
    fun parseTheFixturePdf() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().context
        val pdf = File.createTempFile("phonepe_sample", ".pdf").apply {
            deleteOnExit()
            context.assets.open("phonepe_sample.pdf").use { source ->
                outputStream().use { source.copyTo(it) }
            }
        }

        val extracted = PdfBoxStatementTextExtractor(context).extractText(pdf.absolutePath)
        assertThat(extracted).isInstanceOf(Result.Success::class)

        extractedText = (extracted as Result.Success).data

        val parsed = PhonePeStatementParser().parse(extractedText)
        assertThat(parsed).isInstanceOf(Result.Success::class)
        statement = (parsed as Result.Success).data
    }

    private fun rowPaying(amountPaise: Long): ParsedTransaction =
        statement.transactions.single { it.amountPaise == amountPaise }

    @Test
    fun extractsEveryTransactionInTheStatement() {
        assertThat(statement.transactions.size).isEqualTo(6)
    }

    @Test
    fun sumsToTheStatementTotal() {
        // 10 + 55 + 30 + 1,287.89 + 904 + 65
        assertThat(statement.transactions.sumOf { it.amountPaise }).isEqualTo(235_189L)
    }

    /**
     * Guards the fixture rather than the parser.
     *
     * The CREDIT-in-a-payee test below is only worth running if PdfBox actually emits that payee
     * ahead of the row's own DEBIT token — otherwise a naive search would find the right answer by
     * luck and the test would pass while proving nothing. Asserting the hazard is present keeps a
     * later change to the fixture's layout from quietly defusing it.
     */
    @Test
    fun theFixtureReproducesThePayeeBeforeTypeHazard() {
        val payeeAt = extractedText.indexOf("SHRIRAM CREDIT")
        val typeAt = extractedText.indexOf("DEBIT", startIndex = payeeAt)

        assertThat(payeeAt > 0).isEqualTo(true)
        // The word CREDIT inside the payee comes first, so a bare DEBIT|CREDIT search finds it.
        assertThat(extractedText.indexOf("CREDIT", startIndex = payeeAt) < typeAt).isEqualTo(true)
    }

    /**
     * The details column is printed before the type column, so a search for a bare DEBIT/CREDIT
     * token finds the one inside this payee first. Direction comes from the Type column alone.
     */
    @Test
    fun readsDirectionFromTheTypeColumnAndNotFromThePayeeName() {
        val row = rowPaying(5_500L)

        assertThat(row.rawPayee).isEqualTo("SHRIRAM CREDIT CO-OP SOCIETY")
        assertThat(row.type).isEqualTo(TransactionType.DEBIT)
    }

    @Test
    fun readsACreditFromTheTypeColumn() {
        val row = rowPaying(3_000L)

        assertThat(row.rawPayee).isEqualTo("PARVATI MAHENDRA DAS")
        assertThat(row.type).isEqualTo(TransactionType.CREDIT)
    }

    /** The two phrases that a vocabulary-matching parser dropped from the real statement. */
    @Test
    fun keepsRowsWhoseOpeningPhraseIsNotRecognised() {
        assertThat(rowPaying(128_789L).rawPayee).isEqualTo("Payment to Acme Insurance Brokers")
        assertThat(rowPaying(90_400L).rawPayee).isEqualTo("Mobile recharged 9000000001")
    }

    @Test
    fun recoversAPayeeThatWrappedOntoTheNextLine() {
        assertThat(rowPaying(6_500L).rawPayee).isEqualTo("TOPIC PRODUCTION PRIVATE LIMITED")
    }

    @Test
    fun capturesTransactionReferencesThatAreNotTIds() {
        assertThat(rowPaying(128_789L).transactionRef).isEqualTo("OLEX2607021441483611905461")
        assertThat(rowPaying(90_400L).transactionRef).isEqualTo("NX2607011100374975945309")
    }

    /**
     * Every row carries both identifiers, which is what duplicate detection matches on. A row that
     * lost them would still import, but it would fall back to guessing by payee, amount and time —
     * so losing one to a layout quirk is worth failing a build over.
     */
    @Test
    fun everyRowKeepsItsReferenceAndUtr() {
        assertThat(statement.transactions.count { it.transactionRef != null }).isEqualTo(6)
        assertThat(statement.transactions.count { it.utr != null }).isEqualTo(6)
    }

    /**
     * The last two rows sit on the second page, behind a footer and a repeated set of column
     * headers. Neither may be mistaken for part of a transaction.
     */
    @Test
    fun readsTransactionsFromEveryPage() {
        assertThat(statement.transactions.count { it.amountPaise in listOf(90_400L, 6_500L) })
            .isEqualTo(2)
    }
}
