package com.madtitan94.transactionsparser.feature.upload.presentation.di

import com.madtitan94.transactionsparser.feature.upload.presentation.UploadViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uploadPresentationModule = module {
    viewModelOf(::UploadViewModel)
}
