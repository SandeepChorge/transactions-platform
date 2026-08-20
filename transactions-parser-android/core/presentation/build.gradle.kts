plugins {
    id("transactionsparser.compose")
}

android {
    namespace = "com.madtitan94.transactionsparser.core.presentation"
}

dependencies {
    api(project(":core:domain"))
    // api, not implementation: feature modules launch pickers and permission requests through the
    // helpers in this module, so they need the contracts on their own compile classpath.
    api(libs.androidx.activity.compose)
}
