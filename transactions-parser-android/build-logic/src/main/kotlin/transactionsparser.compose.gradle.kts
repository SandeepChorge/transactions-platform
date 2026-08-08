import com.android.build.api.dsl.LibraryExtension
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("transactionsparser.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = the<LibrariesForLibs>()

extensions.configure<LibraryExtension> {
    buildFeatures {
        compose = true
    }
}

dependencies {
    "implementation"(platform(libs.androidx.compose.bom))
    "implementation"(libs.androidx.compose.ui)
    "implementation"(libs.androidx.compose.ui.graphics)
    "implementation"(libs.androidx.compose.ui.tooling.preview)
    "implementation"(libs.androidx.compose.material3)
    "implementation"(libs.androidx.compose.material.icons.extended)
    "implementation"(libs.androidx.lifecycle.runtime.compose)
    "debugImplementation"(libs.androidx.compose.ui.tooling)
}
