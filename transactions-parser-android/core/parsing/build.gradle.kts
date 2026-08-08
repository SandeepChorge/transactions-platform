plugins {
    id("transactionsparser.jvm.library")
}

dependencies {
    api(project(":core:domain"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
