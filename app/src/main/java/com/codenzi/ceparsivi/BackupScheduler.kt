package com.codenzi.ceparsivi

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackupScheduler {

    fun schedulePeriodicBackup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Sadece Wi-Fi gibi ölçülmeyen ağlarda çalış
            .setRequiresCharging(true)                      // Sadece şarj olurken çalış
            .build()

        val backupWorkRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS) // 24 saatte bir
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AutoBackupWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP, // Mevcut bir plan varsa koru, yoksa yenisini oluştur
            backupWorkRequest
        )
    }

    fun cancelPeriodicBackup(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(AutoBackupWorker.WORK_NAME)
    }
}