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
 * Every schema-version migration TransactionsDatabase has ever needed, in order.
 * verifyRoomMigrations (build-logic) fails the build if a schema version bump here
 * doesn't have a matching entry, so nothing ships without an upgrade path.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
