package com.madtitan94.transactionsparser.feature.settings.presentation.di

import com.madtitan94.transactionsparser.feature.settings.presentation.SettingsViewModel
import com.madtitan94.transactionsparser.feature.settings.presentation.deleted.RecentlyDeletedViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsPresentationModule = module {
    viewModelOf(::SettingsViewModel)
    viewModelOf(::RecentlyDeletedViewModel)
}
