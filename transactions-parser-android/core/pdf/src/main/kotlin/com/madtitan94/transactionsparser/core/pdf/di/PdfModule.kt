package com.madtitan94.transactionsparser.core.pdf.di

import com.madtitan94.transactionsparser.core.domain.parsing.StatementTextExtractor
import com.madtitan94.transactionsparser.core.pdf.PdfBoxStatementTextExtractor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val corePdfModule = module {
    single<StatementTextExtractor> { PdfBoxStatementTextExtractor(androidContext()) }
}
