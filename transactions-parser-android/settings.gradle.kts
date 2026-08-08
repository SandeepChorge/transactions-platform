pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Transactions Parser"
include(":app")
include(":core:domain")
include(":core:presentation")
include(":core:designsystem")
include(":core:database")
include(":core:parsing")
include(":core:pdf")
include(":feature:auth:data")
include(":feature:auth:presentation")
include(":feature:profile:domain")
include(":feature:profile:data")
include(":feature:profile:presentation")
include(":feature:upload:domain")
include(":feature:upload:data")
include(":feature:upload:presentation")
include(":feature:sessions:domain")
include(":feature:sessions:presentation")
include(":feature:categories:presentation")
