package com.madtitan94.transactionsparser.feature.upload.domain.di

import com.madtitan94.transactionsparser.feature.upload.domain.ImportStatementUseCase
import org.koin.dsl.module

val uploadDomainModule = module {
    factory {
        ImportStatementUseCase(
            extractor = get(),
            parserRegistry = get(),
            sessions = get(),
            transactions = get(),
            payees = get(),
            uploadLogs = get()
        )
    }
}
