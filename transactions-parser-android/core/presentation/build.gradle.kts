plugins {
    id("transactionsparser.compose")
}

android {
    namespace = "com.madtitan94.transactionsparser.core.presentation"
}

dependencies {
    api(project(":core:domain"))
}
