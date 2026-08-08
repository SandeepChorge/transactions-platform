package com.madtitan94.transactionsparser.core.database.di

import androidx.room.Room
import com.madtitan94.transactionsparser.core.database.TransactionsDatabase
import com.madtitan94.transactionsparser.core.database.datasource.RoomCategoryDataSource
import com.madtitan94.transactionsparser.core.database.datasource.RoomPayeeDataSource
import com.madtitan94.transactionsparser.core.database.datasource.RoomSessionDataSource
import com.madtitan94.transactionsparser.core.database.datasource.RoomTransactionDataSource
import com.madtitan94.transactionsparser.core.database.datasource.RoomUploadLogDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.SessionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.UploadLogLocalDataSource
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreDatabaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            TransactionsDatabase::class.java,
            "transactions_parser.db"
        ).build()
    }

    single { get<TransactionsDatabase>().categoryDao() }
    single { get<TransactionsDatabase>().payeeDao() }
    single { get<TransactionsDatabase>().sessionDao() }
    single { get<TransactionsDatabase>().transactionDao() }
    single { get<TransactionsDatabase>().uploadLogDao() }

    single<CategoryLocalDataSource> { RoomCategoryDataSource(get()) }
    single<PayeeLocalDataSource> { RoomPayeeDataSource(get()) }
    single<SessionLocalDataSource> { RoomSessionDataSource(get()) }
    single<TransactionLocalDataSource> { RoomTransactionDataSource(get()) }
    single<UploadLogLocalDataSource> { RoomUploadLogDataSource(get()) }
}
