package com.madtitan94.transactionsparser.feature.dashboard.presentation.di

import com.madtitan94.transactionsparser.feature.dashboard.presentation.DashboardViewModel
import com.madtitan94.transactionsparser.feature.dashboard.presentation.builder.DashboardBuilderViewModel
import com.madtitan94.transactionsparser.feature.dashboard.presentation.insight.CategoryInsightViewModel
import com.madtitan94.transactionsparser.feature.dashboard.presentation.manage.DashboardLayoutViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val dashboardPresentationModule = module {
    viewModel { DashboardViewModel(get(), get()) }
    viewModelOf(::DashboardLayoutViewModel)
    viewModelOf(::DashboardBuilderViewModel)
    viewModel { CategoryInsightViewModel(get(), get(), get()) }
}
