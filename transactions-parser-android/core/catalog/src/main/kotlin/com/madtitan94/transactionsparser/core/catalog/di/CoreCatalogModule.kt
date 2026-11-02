package com.madtitan94.transactionsparser.core.catalog.di

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.madtitan94.transactionsparser.core.catalog.DataStoreCategoryCatalogStore
import com.madtitan94.transactionsparser.core.catalog.RemoteConfigSuggestedCategorySource
import com.madtitan94.transactionsparser.core.domain.catalog.AdoptSuggestedCategory
import com.madtitan94.transactionsparser.core.domain.catalog.SuggestedCategorySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The suggestion catalog outlives every screen, so its fetch belongs to the process.
 * SupervisorJob so a failed fetch cannot cancel anything else sharing the scope — nothing else
 * does, and that is the point of giving it one.
 */
val coreCatalogModule = module {
    single(named(CATALOG_SCOPE)) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    single { DataStoreCategoryCatalogStore(androidContext()) }
    single<SuggestedCategorySource> {
        RemoteConfigSuggestedCategorySource(
            remoteConfig = FirebaseRemoteConfig.getInstance(),
            store = get(),
            scope = get(named(CATALOG_SCOPE))
        )
    }
    singleOf(::AdoptSuggestedCategory)
}

private const val CATALOG_SCOPE = "catalogScope"
