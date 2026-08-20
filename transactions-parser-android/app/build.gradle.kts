plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.madtitan94.transactionsparser"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.madtitan94.transactionsparser"
        // minSdk 26 so java.time works without core-library desugaring.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Settings shows the app version, and BuildConfig is where versionName is readable from
        // code. Only :app can carry it — a library module's BuildConfig knows nothing about the
        // application it ends up in, which is why the value is passed down into settingsGraph.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:presentation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:parsing"))
    implementation(project(":core:pdf"))
    implementation(project(":feature:auth:data"))
    implementation(project(":feature:auth:presentation"))
    implementation(project(":feature:profile:domain"))
    implementation(project(":feature:profile:data"))
    implementation(project(":feature:profile:presentation"))
    implementation(project(":feature:upload:domain"))
    implementation(project(":feature:upload:data"))
    implementation(project(":feature:upload:presentation"))
    implementation(project(":feature:sessions:domain"))
    implementation(project(":feature:sessions:presentation"))
    implementation(project(":feature:categories:presentation"))
    implementation(project(":feature:settings:presentation"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
