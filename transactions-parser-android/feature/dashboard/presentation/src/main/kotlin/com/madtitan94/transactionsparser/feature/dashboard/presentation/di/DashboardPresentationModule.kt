package com.madtitan94.transactionsparser.feature.dashboard.presentation.di

import com.madtitan94.transactionsparser.feature.dashboard.presentation.DashboardViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dashboardPresentationModule = module {
    viewModel { DashboardViewModel(get(), get()) }
}
