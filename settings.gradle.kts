// settings.gradle.kts DOSYASININ SON VE DOĞRU HALİ

pluginManagement {
    repositories {
        google()
        // Bu satır, eklentilerin nereden indirileceğini söyler.
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Bu satır, kütüphanelerin (implementation) nereden indirileceğini söyler.
        // Hatanızın ana çözümü burasıdır.
        mavenCentral()
    }
}

rootProject.name = "Cep Arsivi"
include(":app")