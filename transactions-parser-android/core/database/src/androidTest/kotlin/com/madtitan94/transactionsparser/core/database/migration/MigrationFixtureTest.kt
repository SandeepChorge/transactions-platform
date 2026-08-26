package com.madtitan94.transactionsparser.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import com.madtitan94.transactionsparser.core.database.TransactionsDatabase
import com.madtitan94.transactionsparser.core.database.account.LegacyDataClaimer
import com.madtitan94.transactionsparser.core.database.di.buildDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives a whole schema-1 database through every migration at once.
 *
 * [MigrationTest] proves each migration in isolation, on a handful of hand-written rows. That
 * catches a broken statement, but not the things that only appear at scale: 269 transactions
 * spread over two statements, 89 payees each already mapped to a category, names printed several
 * different ways, and a third of the rows deliberately left unmapped. Those are the conditions the
 * one upgrade that actually matters will run under, and they are the ones this test reproduces.
 *
 * **The fixture is synthetic.** It was generated from a real device database and keeps its shape
 * exactly — the same ids, row counts, foreign keys, timestamps, statuses and mapped/unmapped split
 * — while every name, alias, amount, transaction reference and UTR was replaced with generated
 * values. This repository is public, so the real database is not in it and must not be added.
 * Regenerating the fixture means regenerating those values too; do not paste rows from a device.
 */
@RunWith(AndroidJUnit4::class)
class MigrationFixtureTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TransactionsDatabase::class.java
    )

    private var database: TransactionsDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DB)
    }

    /** What the fixture held before any migration ran, read while the v1 database is still open. */
    private data class Seeded(
        val amountTotal: Long,
        val distinctRefs: Int,
        val distinctUtrs: Int
    )

    /**
     * Creates the v1 database and replays the fixture into it, one statement per line.
     *
     * The v1 totals are read here rather than by reopening later, because
     * [MigrationTestHelper.createDatabase] deletes an existing file — calling it a second time
     * would quietly empty the fixture instead of reading it.
     */
    private fun seedFixture(): Seeded {
        val statements = InstrumentationRegistry.getInstrumentation().context.assets
            .open(FIXTURE)
            .bufferedReader()
            .useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("--") }
                    .toList()
            }

        // A fixture that silently stopped loading would make every count below pass for the
        // wrong reason, so the statement count is checked before anything is asserted about it.
        assertThat(statements.size).isEqualTo(TOTAL_ROWS)

        return helper.createDatabase(TEST_DB, 1).use { db ->
            statements.forEach(db::execSQL)
            db.query(
                "SELECT SUM(amountPaise), COUNT(DISTINCT transactionRef), COUNT(DISTINCT utr) " +
                    "FROM transactions"
            ).use { cursor ->
                cursor.moveToFirst()
                Seeded(cursor.getLong(0), cursor.getInt(1), cursor.getInt(2))
            }
        }
    }

    private fun SupportSQLiteDatabase.count(sql: String): Int =
        query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun everyRowOfAWholeSchema1DatabaseSurvivesTheUpgradeToVersion4() {
        seedFixture()

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, *ALL_MIGRATIONS)

        assertThat(db.count("SELECT COUNT(*) FROM categories")).isEqualTo(CATEGORIES)
        assertThat(db.count("SELECT COUNT(*) FROM payees")).isEqualTo(PAYEES)
        assertThat(db.count("SELECT COUNT(*) FROM sessions")).isEqualTo(SESSIONS)
        assertThat(db.count("SELECT COUNT(*) FROM transactions")).isEqualTo(TRANSACTIONS)
        assertThat(db.count("SELECT COUNT(*) FROM upload_logs")).isEqualTo(UPLOAD_LOGS)

        // Nothing is claimed yet: the 1 -> 2 migration cannot see a signed-in account, so every
        // row must be waiting under the legacy owner rather than under a blank one.
        listOf("categories", "payees", "payee_identifiers", "sessions", "transactions", "upload_logs")
            .forEach { table ->
                assertThat(db.count("SELECT COUNT(*) FROM `$table` WHERE ownerId <> '$LEGACY_OWNER_ID'"))
                    .isEqualTo(0)
            }

        db.close()
    }

    @Test
    fun migration3To4BackfillsOneIdentifierPerPayeeAcrossTheWholeDataset() {
        seedFixture()

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, *ALL_MIGRATIONS)

        assertThat(db.count("SELECT COUNT(*) FROM payee_identifiers")).isEqualTo(PAYEES)
        assertThat(db.count("SELECT COUNT(DISTINCT payeeId) FROM payee_identifiers")).isEqualTo(PAYEES)

        // Every identifier points at a payee that still exists, and no payee was left without one.
        assertThat(
            db.count(
                "SELECT COUNT(*) FROM payee_identifiers i " +
                    "LEFT JOIN payees p ON p.id = i.payeeId WHERE p.id IS NULL"
            )
        ).isEqualTo(0)
        assertThat(
            db.count(
                "SELECT COUNT(*) FROM payees p WHERE NOT EXISTS " +
                    "(SELECT 1 FROM payee_identifiers i WHERE i.payeeId = p.id)"
            )
        ).isEqualTo(0)

        // The statement spellings had to come across intact, not be regenerated from the alias —
        // auto-mapping matches on normalizedName, so a rewritten name silently unmaps a payee.
        assertThat(db.count("SELECT COUNT(*) FROM payee_identifiers WHERE rawName <> normalizedName"))
            .isEqualTo(MIXED_CASE_PAYEES)

        db.close()
    }

    @Test
    fun everyMappedTransactionStillPointsAtThePayeeItWasMappedTo() {
        seedFixture()

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, *ALL_MIGRATIONS)

        assertThat(db.count("SELECT COUNT(*) FROM transactions WHERE payeeId IS NOT NULL"))
            .isEqualTo(MAPPED)
        assertThat(db.count("SELECT COUNT(*) FROM transactions WHERE payeeId IS NULL"))
            .isEqualTo(TRANSACTIONS - MAPPED)

        // Rebuilding `payees` in 3 -> 4 renumbers ids if it is done carelessly, which would leave
        // transactions pointing at whatever now holds that id — or at nothing at all.
        assertThat(
            db.count(
                "SELECT COUNT(*) FROM transactions t " +
                    "LEFT JOIN payees p ON p.id = t.payeeId " +
                    "WHERE t.payeeId IS NOT NULL AND p.id IS NULL"
            )
        ).isEqualTo(0)

        // The mapping has to still be *reachable*: the name on the transaction must resolve, via
        // the new identifiers table, to the same payee the row already points at.
        assertThat(
            db.count(
                "SELECT COUNT(*) FROM transactions t " +
                    "JOIN payee_identifiers i ON i.normalizedName = t.normalizedPayee " +
                    "WHERE t.payeeId IS NOT NULL AND i.payeeId <> t.payeeId"
            )
        ).isEqualTo(0)

        // And the rows that were never mapped must stay unmapped rather than being auto-adopted.
        assertThat(
            db.count(
                "SELECT COUNT(*) FROM transactions t " +
                    "JOIN payee_identifiers i ON i.normalizedName = t.normalizedPayee " +
                    "WHERE t.payeeId IS NULL"
            )
        ).isEqualTo(0)

        db.close()
    }

    @Test
    fun amountsAndReferencesComeThroughUntouched() {
        val before = seedFixture()

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, *ALL_MIGRATIONS)

        db.query(
            "SELECT SUM(amountPaise), COUNT(DISTINCT transactionRef), COUNT(DISTINCT utr) FROM transactions"
        ).use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getLong(0)).isEqualTo(before.amountTotal)
            assertThat(cursor.getInt(1)).isEqualTo(before.distinctRefs)
            assertThat(cursor.getInt(2)).isEqualTo(before.distinctUtrs)
        }
        // Every row carries its own reference and UTR, so a lost row would show up here as a
        // shrinking count even if the totals above happened to still line up.
        assertThat(before.distinctRefs).isEqualTo(TRANSACTIONS)
        assertThat(before.distinctUtrs).isEqualTo(TRANSACTIONS)
        assertThat(before.amountTotal).isGreaterThan(0L)

        // 2 -> 3 must leave history alone: totals the user has already seen cannot change because
        // they upgraded, so nothing arrives flagged as a duplicate or excluded.
        assertThat(db.count("SELECT COUNT(*) FROM transactions WHERE isDuplicate = 1")).isEqualTo(0)
        assertThat(db.count("SELECT COUNT(*) FROM transactions WHERE isExcluded = 1")).isEqualTo(0)

        db.close()
    }

    @Test
    fun signingInForTheFirstTimeClaimsEveryLegacyRow() = runTest {
        seedFixture()

        // buildDatabase is what Koin calls in production, so this runs the upgrade the way a real
        // device does — through the registered migrations — rather than through the test helper.
        val db = buildDatabase(context, TEST_DB).also { database = it }
        LegacyDataClaimer(db, db.legacyOwnershipDao()).claimFor(OWNER)

        val readable = db.openHelper.readableDatabase
        listOf("categories", "payees", "payee_identifiers", "sessions", "transactions", "upload_logs")
            .forEach { table ->
                assertThat(readable.count("SELECT COUNT(*) FROM `$table` WHERE ownerId = '$LEGACY_OWNER_ID'"))
                    .isEqualTo(0)
            }

        assertThat(readable.count("SELECT COUNT(*) FROM transactions WHERE ownerId = '$OWNER'"))
            .isEqualTo(TRANSACTIONS)
        assertThat(readable.count("SELECT COUNT(*) FROM payees WHERE ownerId = '$OWNER'"))
            .isEqualTo(PAYEES)
        assertThat(readable.count("SELECT COUNT(*) FROM payee_identifiers WHERE ownerId = '$OWNER'"))
            .isEqualTo(PAYEES)
        assertThat(readable.count("SELECT COUNT(*) FROM categories WHERE ownerId = '$OWNER'"))
            .isEqualTo(CATEGORIES)
        assertThat(readable.count("SELECT COUNT(*) FROM sessions WHERE ownerId = '$OWNER'"))
            .isEqualTo(SESSIONS)
        assertThat(readable.count("SELECT COUNT(*) FROM upload_logs WHERE ownerId = '$OWNER'"))
            .isEqualTo(UPLOAD_LOGS)

        // Claiming is idempotent — a second sign-in must not move rows a second time or fail.
        LegacyDataClaimer(db, db.legacyOwnershipDao()).claimFor(OWNER)
        assertThat(readable.count("SELECT COUNT(*) FROM transactions WHERE ownerId = '$OWNER'"))
            .isEqualTo(TRANSACTIONS)
    }

    private companion object {
        const val TEST_DB = "migration-fixture-test.db"
        const val FIXTURE = "schema1_fixture.sql"
        const val OWNER = "google-sub-first-signin"

        const val CATEGORIES = 22
        const val PAYEES = 89
        const val SESSIONS = 2
        const val TRANSACTIONS = 269
        const val UPLOAD_LOGS = 2
        const val TOTAL_ROWS = CATEGORIES + PAYEES + SESSIONS + TRANSACTIONS + UPLOAD_LOGS

        /** 210 of the 269 rows were already mapped to a payee; the rest never were. */
        const val MAPPED = 210

        /** Payees whose statement name is printed in something other than upper case. */
        const val MIXED_CASE_PAYEES = 32
    }
}
