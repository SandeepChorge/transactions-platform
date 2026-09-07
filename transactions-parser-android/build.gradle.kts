// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Declared here and applied only in :app, which is the module that owns google-services.json
    // and the mapping-file upload. A library module applying either one would either fail for
    // want of the config file or upload a mapping for an artifact it does not produce.
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
