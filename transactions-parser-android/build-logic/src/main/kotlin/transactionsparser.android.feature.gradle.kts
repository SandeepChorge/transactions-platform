import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("transactionsparser.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val libs = the<LibrariesForLibs>()

dependencies {
    "implementation"(project(":core:domain"))
    "implementation"(project(":core:presentation"))
    "implementation"(project(":core:designsystem"))

    "implementation"(libs.androidx.navigation.compose)
    "implementation"(libs.androidx.lifecycle.viewmodel.compose)
    "implementation"(libs.kotlinx.serialization.json)

    "implementation"(platform(libs.koin.bom))
    "implementation"(libs.koin.core)
    "implementation"(libs.koin.android)
    "implementation"(libs.koin.androidx.compose)
}
