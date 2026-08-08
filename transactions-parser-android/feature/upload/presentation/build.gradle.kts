plugins {
    id("transactionsparser.android.feature")
}

android {
    namespace = "com.madtitan94.transactionsparser.feature.upload.presentation"
}

dependencies {
    implementation(project(":feature:upload:domain"))
}
