plugins {
    id("transactionsparser.android.library")
}

android {
    namespace = "com.madtitan94.transactionsparser.core.pdf"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.pdfbox.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // The parser is pulled in for tests only. Extraction and parsing are separate concerns and
    // stay separate in main, but neither is worth much alone: the bug this suite exists to catch
    // lives in how PdfBox orders a page and how the parser reads that order, which is only
    // observable end to end, on a real PDF, through the real extractor.
    androidTestImplementation(project(":core:parsing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.assertk)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
