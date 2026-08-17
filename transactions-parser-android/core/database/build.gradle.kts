plugins {
    id("transactionsparser.android.library")
    id("transactionsparser.room")
}

android {
    namespace = "com.madtitan94.transactionsparser.core.database"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.room.paging)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
