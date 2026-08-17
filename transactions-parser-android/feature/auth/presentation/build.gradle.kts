plugins {
    id("transactionsparser.android.feature")
}

android {
    namespace = "com.madtitan94.transactionsparser.feature.auth.presentation"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Set GOOGLE_WEB_CLIENT_ID in gradle.properties (or ~/.gradle/gradle.properties)
        // with the *web* OAuth client id from your Google Cloud / Firebase console.
        val webClientId = providers.gradleProperty("GOOGLE_WEB_CLIENT_ID")
            .getOrElse("REPLACE_WITH_WEB_CLIENT_ID.apps.googleusercontent.com")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$webClientId\"")
    }
}

dependencies {
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // The Android SDK's org.json is stub-only and throws in unit tests; this is the real one,
    // so the ID token parsing that decides every row's owner id can actually be tested.
    testImplementation(libs.json)
}
