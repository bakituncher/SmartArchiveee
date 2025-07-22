// app/build.gradle.kts DOSYASININ KESİN VE HATASIZ SON HALİ

import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
    id("kotlin-kapt")
    id("com.google.firebase.crashlytics")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.codenzi.ceparsivi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codenzi.ceparsivi"
        minSdk = 24
        targetSdk = 35
        versionCode = 16
        versionName = "1.1.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // DÜZELTME: Değerlerin başındaki/sonundaki olası tırnakları temizle
        fun getSafeProperty(key: String): String {
            return localProperties.getProperty(key)?.trim('"') ?: ""
        }

        buildConfigField("String", "ADMOB_APP_ID", "\"${getSafeProperty("admob.appId")}\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${getSafeProperty("admob.bannerUnitId")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${getSafeProperty("admob.interstitialUnitId")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${getSafeProperty("google.webClientId")}\"")

        manifestPlaceholders["admobAppId"] = getSafeProperty("admob.appId")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packagingOptions {
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/LICENSE")
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("META-INF/license.txt")
        resources.excludes.add("META-INF/NOTICE")
        resources.excludes.add("META-INF/NOTICE.txt")
        resources.excludes.add("META-INF/notice.txt")
        resources.excludes.add("META-INF/ASL2.0")
        resources.excludes.add("META-INF/INDEX.LIST")
        resources.excludes.add("META-INF/io.netty.versions.properties")
    }

    useLibrary("org.apache.http.legacy")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("com.google.android.gms:play-services-ads:23.1.0")
    implementation("com.google.android.ump:user-messaging-platform:2.2.0")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("com.android.billingclient:billing-ktx:7.0.0")
}