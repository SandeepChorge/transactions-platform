package com.madtitan94.transactionsparser.feature.settings.data.di

import com.madtitan94.transactionsparser.feature.settings.data.DataStoreThemeStorage
import com.madtitan94.transactionsparser.feature.settings.domain.ThemeStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val settingsDataModule = module {
    single<ThemeStorage> { DataStoreThemeStorage(androidContext()) }
}
