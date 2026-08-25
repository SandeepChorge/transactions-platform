plugins {
    id("transactionsparser.jvm.library")
    // Applied here rather than in the jvm.library convention because core:domain is the only
    // pure-Kotlin module that serializes anything — the backup format lives here.
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // paging-common is pure Kotlin, so the data source contracts can speak in PagingData without
    // dragging Android into this module. api, because callers receive PagingData from it.
    api(libs.androidx.paging.common)
    // The backup file format is defined here, so its serializer is a dependency of the contract
    // rather than of whichever module happens to write the file.
    implementation(libs.kotlinx.serialization.json)
}
