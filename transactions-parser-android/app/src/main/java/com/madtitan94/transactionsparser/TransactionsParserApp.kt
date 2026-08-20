package com.madtitan94.transactionsparser

import android.app.Application
import com.madtitan94.transactionsparser.core.database.di.coreDatabaseModule
import com.madtitan94.transactionsparser.core.parsing.di.coreParsingModule
import com.madtitan94.transactionsparser.core.pdf.di.corePdfModule
import com.madtitan94.transactionsparser.feature.auth.data.di.authDataModule
import com.madtitan94.transactionsparser.feature.auth.presentation.di.authPresentationModule
import com.madtitan94.transactionsparser.feature.categories.presentation.di.categoriesPresentationModule
import com.madtitan94.transactionsparser.feature.profile.data.di.profileDataModule
import com.madtitan94.transactionsparser.feature.profile.presentation.di.profilePresentationModule
import com.madtitan94.transactionsparser.feature.sessions.presentation.di.sessionsPresentationModule
import com.madtitan94.transactionsparser.feature.settings.presentation.di.settingsPresentationModule
import com.madtitan94.transactionsparser.feature.upload.data.di.uploadDataModule
import com.madtitan94.transactionsparser.feature.upload.domain.di.uploadDomainModule
import com.madtitan94.transactionsparser.feature.upload.presentation.di.uploadPresentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TransactionsParserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TransactionsParserApp)
            modules(
                // core
                coreDatabaseModule,
                coreParsingModule,
                corePdfModule,
                // app
                appModule,
                // features
                authDataModule,
                authPresentationModule,
                profileDataModule,
                profilePresentationModule,
                uploadDomainModule,
                uploadDataModule,
                uploadPresentationModule,
                sessionsPresentationModule,
                categoriesPresentationModule,
                settingsPresentationModule
            )
        }
    }
}
