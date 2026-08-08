plugins {
    id("transactionsparser.android.library")
}

android {
    namespace = "com.madtitan94.transactionsparser.feature.upload.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":feature:upload:domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
