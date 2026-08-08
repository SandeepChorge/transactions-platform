import com.android.build.api.dsl.LibraryExtension
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.android.library")
}

val libs = the<LibrariesForLibs>()

extensions.configure<LibraryExtension> {
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    "testImplementation"(libs.junit.jupiter)
    "testImplementation"(libs.assertk)
    "testImplementation"(libs.turbine)
    "testImplementation"(libs.kotlinx.coroutines.test)
    "testRuntimeOnly"(libs.junit.platform.launcher)
}
