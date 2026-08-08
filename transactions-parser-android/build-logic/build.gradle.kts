plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)

    // Makes the typed `libs` version catalog accessible inside precompiled script plugins.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
