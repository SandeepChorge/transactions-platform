plugins {
    id("transactionsparser.android.library")
}

android {
    namespace = "com.madtitan94.transactionsparser.core.analytics"
}

dependencies {
    implementation(project(":core:domain"))

    // The Firebase BOM, not pinned versions: Analytics and Crashlytics share transitive
    // play-services artifacts, and mismatched versions of those fail at runtime on a device rather
    // than at build time here.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
