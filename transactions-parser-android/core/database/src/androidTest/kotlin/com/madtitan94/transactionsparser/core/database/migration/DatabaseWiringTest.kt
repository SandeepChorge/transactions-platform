package com.madtitan94.transactionsparser.core.database.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.madtitan94.transactionsparser.core.database.TransactionsDatabase
import com.madtitan94.transactionsparser.core.database.di.buildDatabase
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Closes the gap the migration tests leave open.
 *
 * `MigrationTest` builds Room itself, so it proves the migrations are *correct* but not that they
 * are *registered*. Dropping one from `ALL_MIGRATIONS`, or forgetting `addMigrations` in the Koin
 * module, would leave every one of those tests green and still wipe or crash a real upgrade. This
 * one opens a genuine old database through the production builder.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseWiringTest {

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

    @Test
    fun theProductionBuilderCanOpenADatabaseCreatedByTheFirstEverRelease() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO categories VALUES(1,'Grocery')")
            db.execSQL(
                "INSERT INTO payees VALUES(80,'PRAJAKTA ENTERPRISES','PRAJAKTA ENTERPRISES','Prajakta',1)"
            )
            db.execSQL(
                "INSERT INTO sessions VALUES(1,'june.pdf','PHONEPE',1783874080900," +
                    "1780272000000,1782777600000,'COMPLETED')"
            )
            db.execSQL(
                "INSERT INTO transactions VALUES(3,1,1782644460000,'PRAJAKTA ENTERPRISES'," +
                    "'PRAJAKTA ENTERPRISES',20000,'DEBIT','T260628','026430027128',80)"
            )
        }

        // buildDatabase is what Koin calls in production. Opening the writable database is what
        // forces Room to run migrations — without them registered this throws rather than
        // returning, which is exactly the failure this test exists to catch.
        val db = buildDatabase(context, TEST_DB).also { database = it }

        db.openHelper.writableDatabase.query(
            "SELECT amountPaise, transactionRef FROM transactions WHERE id = 3"
        ).use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getLong(0)).isEqualTo(20000L)
            assertThat(cursor.getString(1)).isEqualTo("T260628")
        }
    }

    @Test
    fun everySchemaVersionOnTheUpgradePathIsReachableThroughTheProductionBuilder() {
        // One per shipped schema version below the current one. A new version added without a
        // migration registered in the builder fails here as soon as its predecessor is listed.
        listOf(1, 2).forEach { startVersion ->
            context.deleteDatabase(TEST_DB)
            helper.createDatabase(TEST_DB, startVersion).close()

            val db = buildDatabase(context, TEST_DB)
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM transactions").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "wiring-test.db"
    }
}
