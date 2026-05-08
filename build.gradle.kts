// Archivo build.gradle.kts de NIVEL DE PROYECTO
plugins {
    alias(libs.plugins.android.application)        apply false
    alias(libs.plugins.kotlin.android)             apply false
    alias(libs.plugins.navigation.safeargs)        apply false
    alias(libs.plugins.secrets.gradle)             apply false
    alias(libs.plugins.google.services)            apply false
    alias(libs.plugins.firebase.crashlytics.plugin) apply false
}
