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

    @Test
    fun migrate2To3_keepsRowsAndLeavesExistingHistoryCounted() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO categories VALUES(1,'Grocery')")
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
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, *ALL_MIGRATIONS)

        db.query(
            "SELECT amountPaise, transactionRef, isDuplicate, duplicateOfTransactionId, isExcluded " +
                "FROM transactions WHERE id = 3"
        ).use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getLong(0)).isEqualTo(20000L)
            assertThat(cursor.getString(1)).isEqualTo("T2606281101277674481567")
            // History is left alone: an upgrade must not silently change a total already seen.
            assertThat(cursor.getInt(2)).isEqualTo(0)
            assertThat(cursor.isNull(3)).isEqualTo(true)
            assertThat(cursor.getInt(4)).isEqualTo(0)
        }

        db.close()
    }

    @Test
    fun migrate2To3_allowsRepeatedRefsSoDuplicatesAreKeptRatherThanRejected() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO categories VALUES(1,'Grocery')")
            db.execSQL(
                "INSERT INTO sessions VALUES(1,'june.pdf','PHONEPE',1783874080900,1780272000000,1782777600000,'COMPLETED')"
            )
            db.execSQL(
                "INSERT INTO transactions VALUES(3,1,1782644460000,'SHOP','SHOP',20000,'DEBIT','T-DUP','111',NULL)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, *ALL_MIGRATIONS)

        // The new indices must be non-unique, or flagging a duplicate would be impossible —
        // SQLite would reject the row before it could ever be stored and marked.
        db.execSQL(
            "INSERT INTO transactions (ownerId, sessionId, dateTimeUtcMillis, rawPayee, normalizedPayee, " +
                "amountPaise, type, transactionRef, utr, payeeId, isDuplicate, duplicateOfTransactionId, " +
                "isExcluded, isDeleted) " +
                "VALUES('__legacy__',1,1782644460000,'SHOP','SHOP',20000,'DEBIT','T-DUP','111',NULL,1,3,1,0)"
        )

        db.query("SELECT COUNT(*) FROM transactions WHERE transactionRef = 'T-DUP'").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(2)
        }

        db.close()
    }

    @Test
    fun migrate3To4_movesEachPayeeNameOntoAnIdentifierAndKeepsTheMappingIntact() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO categories VALUES(1,'Grocery')")
            db.execSQL(
                "INSERT INTO payees VALUES(80,'PRAJAKTA ENTERPRISES','PRAJAKTA ENTERPRISES','Prajakta',1)"
            )
            db.execSQL("INSERT INTO payees VALUES(81,'Blinkit','BLINKIT','Groceries app',1)")
            db.execSQL(
                "INSERT INTO sessions VALUES(1,'PhonePe_Statement_Jun2026_Jun2026.pdf','PHONEPE'," +
                    "1783874080900,1780272000000,1782777600000,'COMPLETED')"
            )
            db.execSQL(
                "INSERT INTO transactions VALUES(3,1,1782644460000,'PRAJAKTA ENTERPRISES'," +
                    "'PRAJAKTA ENTERPRISES',20000,'DEBIT','T2606281101277674481567','026430027128',80)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, *ALL_MIGRATIONS)

        // Rebuilding payees must not renumber them, or every transaction would point at nothing.
        db.query("SELECT id, alias, categoryId, ownerId FROM payees ORDER BY id").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getLong(0)).isEqualTo(80L)
            assertThat(cursor.getString(1)).isEqualTo("Prajakta")
            assertThat(cursor.getLong(2)).isEqualTo(1L)
            assertThat(cursor.getString(3)).isEqualTo(LEGACY_OWNER_ID)
            cursor.moveToNext()
            assertThat(cursor.getLong(0)).isEqualTo(81L)
            assertThat(cursor.getString(1)).isEqualTo("Groceries app")
        }

        // Each payee arrives owning exactly the name it was created from — no more, no fewer.
        db.query(
            "SELECT payeeId, rawName, normalizedName, ownerId FROM payee_identifiers ORDER BY payeeId"
        ).use { cursor ->
            assertThat(cursor.count).isEqualTo(2)
            cursor.moveToFirst()
            assertThat(cursor.getLong(0)).isEqualTo(80L)
            assertThat(cursor.getString(1)).isEqualTo("PRAJAKTA ENTERPRISES")
            assertThat(cursor.getString(2)).isEqualTo("PRAJAKTA ENTERPRISES")
            assertThat(cursor.getString(3)).isEqualTo(LEGACY_OWNER_ID)
            cursor.moveToNext()
            assertThat(cursor.getLong(0)).isEqualTo(81L)
            assertThat(cursor.getString(1)).isEqualTo("Blinkit")
            assertThat(cursor.getString(2)).isEqualTo("BLINKIT")
        }

        // The point of preserving ids: an already-mapped transaction stays mapped.
        db.query("SELECT payeeId, amountPaise FROM transactions WHERE id = 3").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getLong(0)).isEqualTo(80L)
            assertThat(cursor.getLong(1)).isEqualTo(20000L)
        }

        db.close()
    }

    @Test
    fun migrate3To4_keepsAStatementNameUniquePerAccountNowThatItLivesOnIdentifiers() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO categories VALUES(1,'Grocery')")
            db.execSQL("INSERT INTO payees VALUES(80,'Blinkit','BLINKIT','Groceries app',1)")
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, *ALL_MIGRATIONS)

        // Uniqueness moved off payees with the columns; it has to still hold, or one name could
        // resolve to two payees and auto-mapping would pick whichever the query saw first.
        val repeatRejected = runCatching {
            db.execSQL(
                "INSERT INTO payee_identifiers (ownerId, payeeId, rawName, normalizedName) " +
                    "VALUES('$LEGACY_OWNER_ID',80,'Blinkit','BLINKIT')"
            )
        }.isFailure
        assertThat(repeatRejected).isEqualTo(true)

        // Per account, though — another account naming the same shop is not a conflict.
        db.execSQL("INSERT INTO categories (ownerId, name, isDeleted) VALUES('google-sub-b','Grocery',0)")
        db.execSQL(
            "INSERT INTO payees (ownerId, alias, categoryId, isDeleted) " +
                "VALUES('google-sub-b','Blinkit',(SELECT id FROM categories WHERE ownerId='google-sub-b'),0)"
        )
        db.execSQL(
            "INSERT INTO payee_identifiers (ownerId, payeeId, rawName, normalizedName) " +
                "VALUES('google-sub-b',(SELECT id FROM payees WHERE ownerId='google-sub-b'),'Blinkit','BLINKIT')"
        )

        db.query("SELECT COUNT(*) FROM payee_identifiers WHERE normalizedName = 'BLINKIT'").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(2)
        }

        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
