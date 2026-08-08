package com.madtitan94.transactionsparser.feature.auth.presentation.di

import com.madtitan94.transactionsparser.feature.auth.presentation.BuildConfig
import com.madtitan94.transactionsparser.feature.auth.presentation.GoogleCredentialHelper
import com.madtitan94.transactionsparser.feature.auth.presentation.LoginViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authPresentationModule = module {
    single { GoogleCredentialHelper(BuildConfig.GOOGLE_WEB_CLIENT_ID) }
    viewModelOf(::LoginViewModel)
}
