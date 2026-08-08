package com.madtitan94.transactionsparser.core.parsing.di

import com.madtitan94.transactionsparser.core.domain.parsing.StatementParserRegistry
import com.madtitan94.transactionsparser.core.parsing.GooglePayStatementParser
import com.madtitan94.transactionsparser.core.parsing.PhonePeStatementParser
import org.koin.dsl.module

val coreParsingModule = module {
    single {
        StatementParserRegistry(
            parsers = listOf(
                PhonePeStatementParser(),
                GooglePayStatementParser()
            )
        )
    }
}
