import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Versioning is CI-driven (issue #9): CI computes both fields and passes them in as
// environment variables. version.properties owns MAJOR/MINOR only — PATCH is the CI run
// counter, so it exists nowhere in the repo.
//
// providers.* rather than System.getenv/File.readText throughout: Gradle's configuration
// cache tracks provider reads and invalidates when they change, but cannot see a direct
// getenv. A cached configuration would otherwise keep stamping a stale version into
// every artifact, which is invisible until someone checks a published build.
val versionProperties = providers.fileContents(
    rootProject.layout.projectDirectory.file("version.properties")
).asText.map { text -> Properties().apply { load(text.reader()) } }.get()

// Deliberately unsuffixed, matching the release format. A local build reads 1.0.0, not
// 1.0.0-dev — this app ships one version format on every channel.
val localVersionName = "${versionProperties.getProperty("major")}.${versionProperties.getProperty("minor")}.0"

// Release signing comes entirely from the environment. CI decodes the KEYSTORE_BASE64
// secret to a file outside the source tree and exports its path as KEYSTORE_FILE.
val keystoreFile = providers.environmentVariable("KEYSTORE_FILE").orNull
val keystorePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull

val hasReleaseSigning = listOf(keystoreFile, keystorePassword, releaseKeyAlias, releaseKeyPassword)
    .none { it.isNullOrBlank() }

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
        versionCode = providers.environmentVariable("APP_VERSION_CODE").orNull?.toInt() ?: 1
        versionName = providers.environmentVariable("APP_VERSION_STRING").orNull ?: localVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreFile!!)
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Left unset when the environment has no keystore, so a local `assembleRelease`
            // still works. CI cannot reach that state — see the guard below.
            signingConfig = signingConfigs.findByName("release")

            // R8 on as of issue #9 Phase 7. The hazards it was held back for are all
            // runtime-only — PdfBox-Android resolving fonts and CMaps by name, and the
            // eighteen @Serializable classes (nine navigation routes, nine backup-format
            // models) reached through generated serializers. None of them can fail this
            // build; they fail on a device or, worse, in a backup file. The keep rules
            // are in proguard-rules.pro and the phase's verify step is what proves them.
            isMinifyEnabled = true

            // Only meaningful with minification on, and it is the half that touches
            // PdfBox's shipped assets least: resource shrinking works on res/, while
            // PdfBox reads its fonts and CMaps out of assets/, which is not shrunk.
            isShrinkResources = true
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

// A release build type with no signing config does not fall back to the debug key — it
// emits a silently *unsigned* artifact. The build goes green, and the problem only surfaces
// at upload time with nothing pointing back at the cause. Fail before the task runs instead.
//
// Scoped to CI so a local `assembleRelease`, and Android Studio's signed-bundle wizard
// (which injects signing rather than exporting it to the environment), keep working.
val isCi = providers.environmentVariable("CI").orNull.toBoolean()

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst {
        check(!isCi || hasReleaseSigning) {
            "Release signing is not configured. CI must export KEYSTORE_FILE, KEYSTORE_PASSWORD, " +
                "KEY_ALIAS and KEY_PASSWORD (KEYSTORE_FILE is written by the decode step)."
        }
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
