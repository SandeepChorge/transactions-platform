package com.madtitan94.transactionsparser.core.database.migration

import androidx.room.migration.Migration

/**
 * Every schema-version migration TransactionsDatabase has ever needed, in order.
 * verifyRoomMigrations (build-logic) fails the build if a schema version bump here
 * doesn't have a matching entry, so nothing ships without an upgrade path.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
