import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.google.devtools.ksp")
}

val libs = the<LibrariesForLibs>()

dependencies {
    "implementation"(libs.androidx.room.runtime)
    "implementation"(libs.androidx.room.ktx)
    "ksp"(libs.androidx.room.compiler)
    "androidTestImplementation"(libs.androidx.room.testing)
    "androidTestImplementation"(libs.androidx.junit)
    "androidTestImplementation"(libs.androidx.test.runner)
    "androidTestImplementation"(libs.assertk)
    "androidTestImplementation"(libs.kotlinx.coroutines.test)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// MigrationTestHelper loads the exported schema history from the test APK's assets,
// so every past version has to ship inside it.
plugins.withId("com.android.library") {
    extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
        sourceSets.getByName("androidTest").assets.srcDir(layout.projectDirectory.dir("schemas"))
    }
}

// Fails the build if a Room schema version bump doesn't have a matching Migration
// registered in Migrations.kt, so a schema change can never ship without an upgrade path.
val verifyRoomMigrations by tasks.registering {
    group = "verification"
    description = "Fails if a Room schema version bump is missing a Migration."

    val schemasDir = layout.projectDirectory.dir("schemas")
    val migrationsFile = layout.projectDirectory.file(
        "src/main/kotlin/com/madtitan94/transactionsparser/core/database/migration/Migrations.kt"
    )
    inputs.dir(schemasDir).optional()
    inputs.file(migrationsFile).optional()

    doLast {
        val schemaRoot = schemasDir.asFile
        if (!schemaRoot.exists()) return@doLast

        val versions = schemaRoot.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .toSortedSet()

        if (versions.size < 2) return@doLast

        val migrationsSource = migrationsFile.asFile.takeIf { it.exists() }?.readText().orEmpty()
        val declaredPairs = Regex("""Migration\(\s*(\d+)\s*,\s*(\d+)\s*\)""")
            .findAll(migrationsSource)
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .toSet()

        val missing = versions.zipWithNext().filterNot { it in declaredPairs }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Room schema changed but is missing a Migration for: " +
                    missing.joinToString { (from, to) -> "$from -> $to" } +
                    ". Add it to Migrations.kt (with a MigrationTestHelper test) before merging " +
                    "or the app will crash on upgrade for anyone on an older version."
            )
        }
    }
}

// verifyRoomMigrations reads the exported schema JSON, so it must run *after* KSP
// regenerates it — whether invoked directly or transitively through `check`.
verifyRoomMigrations.configure {
    dependsOn(tasks.matching { it.name.startsWith("ksp") && it.name.contains("Kotlin") })
}

tasks.named("check") {
    dependsOn(verifyRoomMigrations)
}
