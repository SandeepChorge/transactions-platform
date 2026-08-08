package com.madtitan94.transactionsparser.feature.profile.data.di

import com.madtitan94.transactionsparser.feature.profile.data.DataStoreProfileStorage
import com.madtitan94.transactionsparser.feature.profile.domain.ProfileStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val profileDataModule = module {
    single<ProfileStorage> { DataStoreProfileStorage(androidContext()) }
}
