package com.madtitan94.transactionsparser.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.madtitan94.transactionsparser.core.database.dao.CategoryDao
import com.madtitan94.transactionsparser.core.database.dao.LegacyOwnershipDao
import com.madtitan94.transactionsparser.core.database.dao.PayeeDao
import com.madtitan94.transactionsparser.core.database.dao.PayeeIdentifierDao
import com.madtitan94.transactionsparser.core.database.dao.SessionDao
import com.madtitan94.transactionsparser.core.database.dao.TransactionDao
import com.madtitan94.transactionsparser.core.database.dao.UploadLogDao
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeIdentifierEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import com.madtitan94.transactionsparser.core.database.entity.UploadLogEntity

@Database(
    entities = [
        CategoryEntity::class,
        PayeeEntity::class,
        PayeeIdentifierEntity::class,
        SessionEntity::class,
        TransactionEntity::class,
        UploadLogEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class TransactionsDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun payeeDao(): PayeeDao
    abstract fun payeeIdentifierDao(): PayeeIdentifierDao
    abstract fun sessionDao(): SessionDao
    abstract fun transactionDao(): TransactionDao
    abstract fun uploadLogDao(): UploadLogDao
    abstract fun legacyOwnershipDao(): LegacyOwnershipDao
}
