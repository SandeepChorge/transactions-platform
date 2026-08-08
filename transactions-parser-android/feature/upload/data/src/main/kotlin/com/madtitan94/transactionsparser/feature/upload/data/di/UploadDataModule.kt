package com.madtitan94.transactionsparser.feature.upload.data.di

import com.madtitan94.transactionsparser.feature.upload.data.ContentResolverStatementFileDataSource
import com.madtitan94.transactionsparser.feature.upload.domain.StatementFileDataSource
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val uploadDataModule = module {
    single<StatementFileDataSource> { ContentResolverStatementFileDataSource(androidContext()) }
}
