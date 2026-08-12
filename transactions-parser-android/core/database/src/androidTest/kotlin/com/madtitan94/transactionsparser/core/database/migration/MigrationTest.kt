package com.madtitan94.transactionsparser.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.madtitan94.transactionsparser.core.database.TransactionsDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the upgrade path for people who already have data. The seed rows below are copied
 * from a real v1 database export, so a regression here means real transactions would be lost.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TransactionsDatabase::class.java
    )

    @Test
    fun migrate1To2_keepsExistingRowsAndTagsThemLegacy() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO categories VALUES(1,'Grocery')")
            db.execSQL("INSERT INTO categories VALUES(17,'Gas bill')")
            db.execSQL(
                "INSERT INTO payees VALUES(80,'PRAJAKTA ENTERPRISES','PRAJAKTA ENTERPRISES','Prajakta',1)"
            )
            db.execSQL(
                "INSERT INTO sessions VALUES(1,'PhonePe_Statement_Jun2026_Jun2026.pdf','PHONEPE'," +
                    "1783874080900,1780272000000,1782777600000,'COMPLETED')"
            )
            db.execSQL(
                "INSERT INTO transactions VALUES(3,1,1782644460000,'PRAJAKTA ENTERPRISES'," +
                    "'PRAJAKTA ENTERPRISES',20000,'DEBIT','T2606281101277674481567','026430027128',80)"
            )
            db.execSQL(
                "INSERT INTO upload_logs VALUES(1,'PhonePe_Statement_Jun2026_Jun2026.pdf'," +
                    "1783874081549,1,'PHONEPE',NULL,1)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, *ALL_MIGRATIONS)

        db.query("SELECT COUNT(*) FROM transactions").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(1)
        }

        // The row must come through byte-for-byte, not just survive as a count.
        db.query(
            "SELECT amountPaise, rawPayee, transactionRef, payeeId, ownerId, isDeleted, deletedAtMillis " +
                "FROM transactions WHERE id = 3"
        ).use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getLong(0)).isEqualTo(20000L)
            assertThat(cursor.getString(1)).isEqualTo("PRAJAKTA ENTERPRISES")
            assertThat(cursor.getString(2)).isEqualTo("T2606281101277674481567")
            assertThat(cursor.getLong(3)).isEqualTo(80L)
            assertThat(cursor.getString(4)).isEqualTo(LEGACY_OWNER_ID)
            assertThat(cursor.getInt(5)).isEqualTo(0)
            assertThat(cursor.isNull(6)).isEqualTo(true)
        }

        listOf("categories", "payees", "sessions", "upload_logs").forEach { table ->
            db.query("SELECT COUNT(*) FROM `$table` WHERE ownerId = '$LEGACY_OWNER_ID'").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(if (table == "categories") 2 else 1)
            }
        }

        db.close()
    }

    @Test
    fun migrate1To2_makesCategoryAndPayeeNamesUniquePerAccountInsteadOfGlobally() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO categories VALUES(1,'Grocery')")
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, *ALL_MIGRATIONS)

        // Same name under a different owner is now allowed; a repeat under the same owner is not.
        db.execSQL("INSERT INTO categories (ownerId, name, isDeleted) VALUES('google-sub-b','Grocery',0)")

        val duplicateRejected = runCatching {
            db.execSQL("INSERT INTO categories (ownerId, name, isDeleted) VALUES('google-sub-b','Grocery',0)")
        }.isFailure
        assertThat(duplicateRejected).isEqualTo(true)

        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
