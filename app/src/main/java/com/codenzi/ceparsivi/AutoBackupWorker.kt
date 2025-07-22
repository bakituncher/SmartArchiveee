package com.codenzi.ceparsivi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo // YENİ EKLENDİ
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoBackupWorker(private val appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val WORK_NAME = "com.codenzi.ceparsivi.AutoBackupWorker"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "BackupChannel"
        private const val TAG = "AutoBackupWorker"
    }

    // YENİ EKLENDİ: Çökme raporu bu metodun içindeki hatadan kaynaklanıyordu.
    // Artık hizmetin türünü açıkça belirtiyoruz.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val progress = appContext.getString(R.string.backup_notification_progress_title)
        createNotificationChannel()

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(progress)
            .setTicker(progress)
            .setSmallIcon(R.drawable.ic_cloud_upload)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // BURASI DEĞİŞTİ: Android 10 (Q) ve üzeri için hizmet türünü (dataSync) ekliyoruz.
        // Bu, InvalidForegroundServiceTypeException hatasını çözer.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }


    override suspend fun doWork(): Result {
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(appContext)
        if (lastSignedInAccount == null) {
            Log.d(TAG, "No signed-in user found. Skipping backup.")
            return Result.success()
        }

        val driveHelper = GoogleDriveHelper(appContext, lastSignedInAccount)

        return try {
            Log.d(TAG, "Starting automatic backup...")

            val backupResult = driveHelper.backupData()

            if (backupResult) {
                Log.d(TAG, "Automatic backup successful.")
                showCompletionNotification(
                    appContext.getString(R.string.backup_success_title),
                    appContext.getString(R.string.backup_success_message, getCurrentTimestamp())
                )
                return Result.success()
            } else {
                Log.e(TAG, "Automatic backup failed.")
                showCompletionNotification(
                    appContext.getString(R.string.backup_failed_title),
                    appContext.getString(R.string.backup_failed_message)
                )
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during automatic backup", e)
            showCompletionNotification(
                appContext.getString(R.string.backup_failed_title),
                appContext.getString(R.string.backup_failed_message_exception, e.localizedMessage)
            )
            return Result.failure()
        }
    }


    private fun showCompletionNotification(title: String, message: String) {
        createNotificationChannel()

        if (ActivityCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot show completion notification.")
            return
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_cloud_done)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = appContext.getString(R.string.backup_channel_name)
            val descriptionText = appContext.getString(R.string.backup_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date())
    }
}