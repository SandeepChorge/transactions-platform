package com.madtitan94.transactionsparser.feature.auth.data.di

import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.feature.auth.data.DataStoreSessionStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val authDataModule = module {
    single<SessionStorage> { DataStoreSessionStorage(androidContext()) }
}
