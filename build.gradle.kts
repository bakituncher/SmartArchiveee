// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // YENİ EKLENDİ
    alias(libs.plugins.google.services) apply false
    id("com.google.firebase.crashlytics") version "3.0.1" apply false
}