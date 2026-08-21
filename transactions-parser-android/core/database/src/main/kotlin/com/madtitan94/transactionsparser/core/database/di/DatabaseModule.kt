package com.madtitan94.transactionsparser.core.database.di

import android.content.Context
import androidx.room.Room
import com.madtitan94.transactionsparser.core.database.TransactionsDatabase
import com.madtitan94.transactionsparser.core.database.account.ActiveAccountProvider
import com.madtitan94.transactionsparser.core.database.account.LegacyDataClaimer
import com.madtitan94.transactionsparser.core.database.datasource.RoomCategoryDataSource
import com.madtitan94.transactionsparser.core.database.datasource.RoomPayeeDataSource
import com.madtitan94.transactionsparser.core.database.datasource.RoomSessionDataSource
import com.madtitan94.transactionsparser.core.database.datasource.RoomTransactionDataSource
import com.madtitan94.transactionsparser.core.database.datasource.RoomUploadLogDataSource
import com.madtitan94.transactionsparser.core.database.migration.ALL_MIGRATIONS
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.SessionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.UploadLogLocalDataSource
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

const val DATABASE_NAME = "transactions_parser.db"

/**
 * The one place the app's database is built.
 *
 * Extracted from the Koin module and given a [name] parameter so a test can exercise this exact
 * builder — the migration tests construct Room themselves, so without this a migration missing
 * from [ALL_MIGRATIONS] *here* would pass every test and only fail on a real upgrade.
 */
fun buildDatabase(context: Context, name: String = DATABASE_NAME): TransactionsDatabase =
    Room.databaseBuilder(context, TransactionsDatabase::class.java, name)
        .addMigrations(*ALL_MIGRATIONS)
        .build()

val coreDatabaseModule = module {
    single { buildDatabase(androidContext()) }

    single { get<TransactionsDatabase>().categoryDao() }
    single { get<TransactionsDatabase>().payeeDao() }
    single { get<TransactionsDatabase>().payeeIdentifierDao() }
    single { get<TransactionsDatabase>().sessionDao() }
    single { get<TransactionsDatabase>().transactionDao() }
    single { get<TransactionsDatabase>().uploadLogDao() }
    single { get<TransactionsDatabase>().legacyOwnershipDao() }

    single { ActiveAccountProvider(get()) }
    single { LegacyDataClaimer(get(), get()) }

    single<CategoryLocalDataSource> { RoomCategoryDataSource(get(), get()) }
    single<PayeeLocalDataSource> { RoomPayeeDataSource(get(), get(), get(), get()) }
    single<SessionLocalDataSource> { RoomSessionDataSource(get(), get()) }
    single<TransactionLocalDataSource> { RoomTransactionDataSource(get(), get()) }
    single<UploadLogLocalDataSource> { RoomUploadLogDataSource(get(), get()) }
}
