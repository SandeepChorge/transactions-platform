plugins {
    id("transactionsparser.android.library")
}

android {
    namespace = "com.madtitan94.transactionsparser.core.catalog"
}

dependencies {
    implementation(project(":core:domain"))

    // Same BOM as :core:analytics, so Remote Config and Analytics share one set of
    // play-services artifacts. A second pinned version fails on a device, not here.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
