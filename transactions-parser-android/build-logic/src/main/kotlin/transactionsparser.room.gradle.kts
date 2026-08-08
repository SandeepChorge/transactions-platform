import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.google.devtools.ksp")
}

val libs = the<LibrariesForLibs>()

dependencies {
    "implementation"(libs.androidx.room.runtime)
    "implementation"(libs.androidx.room.ktx)
    "ksp"(libs.androidx.room.compiler)
}
