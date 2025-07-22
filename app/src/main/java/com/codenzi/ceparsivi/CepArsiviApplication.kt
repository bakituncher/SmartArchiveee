package com.codenzi.ceparsivi

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
// Bu import satırı doğru ve gereklidir.
import com.codenzi.ceparsivi.BuildConfig

class CepArsiviApplication : Application() {

    private var areAdsInitialized = false

    override fun onCreate() {
        super.onCreate()
        val theme = ThemeManager.getTheme(this)
        ThemeManager.applyTheme(theme)
        createBackupNotificationChannel()
    }

    fun initializeMobileAds() {
        if (areAdsInitialized) return

        // Sadece DEBUG modunda çalışacak test cihazı ayarı.
        if (BuildConfig.DEBUG) {
            val testDeviceIds = listOf("BCF3B4664E529BDE4CC3E6B2CB090F7B") // Kendi test cihazı ID'niz
            val configuration = RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
            MobileAds.setRequestConfiguration(configuration)
        }

        MobileAds.initialize(this) {}
        areAdsInitialized = true
    }

    private fun createBackupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "BackupChannel"
            val channelName = getString(R.string.backup_channel_name)
            val channelDescription = getString(R.string.backup_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}