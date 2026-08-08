package com.madtitan94.transactionsparser.feature.categories.presentation.di

import com.madtitan94.transactionsparser.feature.categories.presentation.CategoriesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val categoriesPresentationModule = module {
    viewModelOf(::CategoriesViewModel)
}
