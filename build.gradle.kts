// Plugin versions live in gradle/libs.versions.toml. kotlin-jvm is declared here so
// :domain can apply kotlin("jvm") without a version and share the Android Kotlin version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.google.services) apply false
}
