plugins {
    id("transactionsparser.android.feature")
}

android {
    namespace = "com.madtitan94.transactionsparser.feature.sessions.presentation"
}

dependencies {
    implementation(project(":feature:sessions:domain"))
}
