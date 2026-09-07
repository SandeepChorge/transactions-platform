package com.madtitan94.transactionsparser.feature.sessions.presentation.di

import com.madtitan94.transactionsparser.feature.sessions.presentation.detail.SessionDetailViewModel
import com.madtitan94.transactionsparser.feature.sessions.presentation.history.SessionsHistoryViewModel
import com.madtitan94.transactionsparser.feature.sessions.presentation.payee.PayeeDetailViewModel
import com.madtitan94.transactionsparser.feature.sessions.presentation.payee.PayeeDirectoryViewModel
import com.madtitan94.transactionsparser.feature.sessions.presentation.search.TransactionSearchViewModel
import com.madtitan94.transactionsparser.feature.sessions.presentation.uploadhistory.UploadHistoryViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sessionsPresentationModule = module {
    viewModelOf(::SessionsHistoryViewModel)
    viewModelOf(::SessionDetailViewModel)
    viewModelOf(::PayeeDetailViewModel)
    viewModelOf(::PayeeDirectoryViewModel)
    viewModelOf(::TransactionSearchViewModel)
    viewModelOf(::UploadHistoryViewModel)
}
