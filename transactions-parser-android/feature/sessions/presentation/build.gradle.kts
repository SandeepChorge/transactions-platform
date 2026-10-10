plugins {
    id("transactionsparser.android.feature")
}

android {
    namespace = "com.madtitan94.transactionsparser.feature.sessions.presentation"
}

dependencies {
    implementation(project(":feature:sessions:domain"))
    implementation(libs.androidx.paging.compose)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.assertk)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
