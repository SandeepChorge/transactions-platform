plugins {
    id("transactionsparser.android.feature")
}

android {
    namespace = "com.madtitan94.transactionsparser.feature.profile.presentation"
}

dependencies {
    implementation(project(":feature:profile:domain"))
}
