plugins {
    id("transactionsparser.jvm.library")
}

dependencies {
    // paging-common is pure Kotlin, so the data source contracts can speak in PagingData without
    // dragging Android into this module. api, because callers receive PagingData from it.
    api(libs.androidx.paging.common)
}
