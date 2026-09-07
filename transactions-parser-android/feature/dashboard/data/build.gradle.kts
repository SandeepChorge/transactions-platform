plugins {
    id("transactionsparser.android.library")
    // A user-built dashboard carries a name the user typed, so its stored form has to survive
    // commas and quotes — which makes it JSON rather than a delimited string.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.madtitan94.transactionsparser.feature.dashboard.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":feature:dashboard:domain"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
