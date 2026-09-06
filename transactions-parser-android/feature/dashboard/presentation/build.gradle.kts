plugins {
    id("transactionsparser.android.feature")
}

android {
    namespace = "com.madtitan94.transactionsparser.feature.dashboard.presentation"
}

dependencies {
    implementation(project(":feature:dashboard:domain"))
}
