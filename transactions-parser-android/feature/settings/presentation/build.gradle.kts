plugins {
    id("transactionsparser.android.feature")
}

android {
    namespace = "com.madtitan94.transactionsparser.feature.settings.presentation"
}

dependencies {
    implementation(project(":feature:settings:domain"))
}
