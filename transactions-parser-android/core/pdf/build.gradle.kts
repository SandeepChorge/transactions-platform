plugins {
    id("transactionsparser.android.library")
}

android {
    namespace = "com.madtitan94.transactionsparser.core.pdf"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.pdfbox.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
