import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
}

val libs = the<LibrariesForLibs>()

extensions.configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

extensions.configure<KotlinJvmProjectExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    "implementation"(libs.kotlinx.coroutines.core)
    "testImplementation"(libs.junit.jupiter)
    "testImplementation"(libs.assertk)
    "testImplementation"(libs.turbine)
    "testImplementation"(libs.kotlinx.coroutines.test)
    "testRuntimeOnly"(libs.junit.platform.launcher)
}
