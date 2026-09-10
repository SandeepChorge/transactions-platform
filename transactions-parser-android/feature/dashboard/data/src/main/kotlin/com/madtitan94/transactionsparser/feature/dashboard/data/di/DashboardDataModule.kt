package com.madtitan94.transactionsparser.feature.dashboard.data.di

import com.madtitan94.transactionsparser.feature.dashboard.data.DataStoreDashboardPreferences
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dashboardDataModule = module {
    single { DataStoreDashboardPreferences(androidContext(), get()) }
    single<DashboardPreferences> { get<DataStoreDashboardPreferences>() }
}
