package com.madtitan94.transactionsparser.feature.profile.presentation.di

import com.madtitan94.transactionsparser.feature.profile.presentation.ProfileViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val profilePresentationModule = module {
    viewModelOf(::ProfileViewModel)
}
