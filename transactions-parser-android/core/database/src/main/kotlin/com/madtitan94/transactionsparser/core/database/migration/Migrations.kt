package com.madtitan94.transactionsparser.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Marks rows that predate multi-account support. Nobody's Google id can collide with this,
 * so legacy rows stay invisible until [com.madtitan94.transactionsparser.core.database.account.LegacyDataClaimer]
 * hands them to the first account that signs in after the upgrade.
 */
const val LEGACY_OWNER_ID: String = "__legacy__"

/**
 * Adds per-account ownership and soft delete to every table.
 *
 * Runs inside `databaseBuilder().build()`, which has no access to the signed-in session, so
 * existing rows are tagged [LEGACY_OWNER_ID] here and reassigned to the real account later.
 * Every statement is additive — no table is dropped or rebuilt, so existing rows survive intact.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val tables = listOf("categories", "payees", "sessions", "transactions", "upload_logs")

        tables.forEach { table ->
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `ownerId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `deletedAtMillis` INTEGER")
            db.execSQL("UPDATE `$table` SET `ownerId` = '$LEGACY_OWNER_ID'")
        }

        // Uniqueness is now per-account: two accounts may each have a "Groceries" category.
        db.execSQL("DROP INDEX IF EXISTS `index_categories_name`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_ownerId_name` ON `categories` (`ownerId`, `name`)")
        db.execSQL("DROP INDEX IF EXISTS `index_payees_normalizedName`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payees_ownerId_normalizedName` ON `payees` (`ownerId`, `normalizedName`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_ownerId` ON `sessions` (`ownerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ownerId` ON `transactions` (`ownerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_upload_logs_ownerId` ON `upload_logs` (`ownerId`)")
    }
}

/**
 * Adds duplicate detection to transactions.
 *
 * Existing rows are left un-flagged rather than scanned retroactively: detection runs at import,
 * so history keeps whatever totals it already had and an upgrade can't silently change a number
 * the user has already seen. The indices are non-unique on purpose — duplicates are kept and
 * flagged, never rejected by the database.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isDuplicate` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `duplicateOfTransactionId` INTEGER")
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isExcluded` INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_ownerId_transactionRef` " +
                "ON `transactions` (`ownerId`, `transactionRef`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_ownerId_utr` " +
                "ON `transactions` (`ownerId`, `utr`)"
        )
    }
}

/**
 * Moves statement names off `payees` and into their own `payee_identifiers` table.
 *
 * Before this, a payee *was* its statement name — one row, one name, unique per account. That
 * made a payee impossible to merge: two names for the same shop were two payees by definition.
 * After it, `payees` holds only what the user decided (alias, category) and every statement name
 * is a row pointing at one, so merging is a re-pointing of identifiers rather than a rewrite.
 *
 * Existing rows migrate one-for-one — each payee gets exactly the identifier it was created
 * from, keeping its own id — so nothing auto-mapped before the upgrade stops auto-mapping after
 * it, and `transactions.payeeId` never has to move.
 *
 * `payees` is rebuilt rather than altered because SQLite before 3.35 cannot drop a column, and
 * leaving the name columns behind would leave two sources of truth for the same fact. Ids are
 * carried across explicitly, so the transactions pointing at them stay pointing at them.
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `payee_identifiers` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerId` TEXT NOT NULL, " +
                "`payeeId` INTEGER NOT NULL, " +
                "`rawName` TEXT NOT NULL, " +
                "`normalizedName` TEXT NOT NULL, " +
                "FOREIGN KEY(`payeeId`) REFERENCES `payees`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_payee_identifiers_ownerId_normalizedName` " +
                "ON `payee_identifiers` (`ownerId`, `normalizedName`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_payee_identifiers_payeeId` " +
                "ON `payee_identifiers` (`payeeId`)"
        )

        // One identifier per existing payee: the statement name it was mapped from.
        db.execSQL(
            "INSERT INTO `payee_identifiers` (`ownerId`, `payeeId`, `rawName`, `normalizedName`) " +
                "SELECT `ownerId`, `id`, `rawName`, `normalizedName` FROM `payees`"
        )

        // Rebuild payees without the name columns, preserving ids for transactions.payeeId.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `payees_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`ownerId` TEXT NOT NULL, " +
                "`alias` TEXT NOT NULL, " +
                "`categoryId` INTEGER NOT NULL, " +
                "`isDeleted` INTEGER NOT NULL, " +
                "`deletedAtMillis` INTEGER, " +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL(
            "INSERT INTO `payees_new` " +
                "(`id`, `ownerId`, `alias`, `categoryId`, `isDeleted`, `deletedAtMillis`) " +
                "SELECT `id`, `ownerId`, `alias`, `categoryId`, `isDeleted`, `deletedAtMillis` " +
                "FROM `payees`"
        )
        db.execSQL("DROP TABLE `payees`")
        db.execSQL("ALTER TABLE `payees_new` RENAME TO `payees`")

        // The old unique index went with the old table; uniqueness now lives on identifiers.
        // ownerId gets its own index because it no longer rides along on that unique pair.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payees_ownerId` ON `payees` (`ownerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payees_categoryId` ON `payees` (`categoryId`)")
    }
}

/**
 * Every schema-version migration TransactionsDatabase has ever needed, in order.
 * verifyRoomMigrations (build-logic) fails the build if a schema version bump here
 * doesn't have a matching entry, so nothing ships without an upgrade path.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
