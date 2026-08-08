plugins {
    id("transactionsparser.android.library")
}

android {
    namespace = "com.madtitan94.transactionsparser.feature.profile.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":feature:profile:domain"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
