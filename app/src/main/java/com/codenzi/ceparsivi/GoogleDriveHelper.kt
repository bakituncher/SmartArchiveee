package com.codenzi.ceparsivi

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.google.api.services.drive.model.File as DriveFile
import java.util.zip.ZipEntry
import androidx.core.content.edit

// Geri yükleme işleminin sonucunu bildiren, daha anlaşılır bir yapı
enum class RestoreResult {
    SUCCESS,
    NO_BACKUP_FOUND,
    DOWNLOAD_FAILED,
    VALIDATION_FAILED, // Yedek dosyası bozuk veya geçersiz
    RESTORE_FAILED,    // Dosyalar yerine yazılamadı
    UNKNOWN_ERROR
}

class GoogleDriveHelper(private val context: Context, account: GoogleSignInAccount) {

    private val drive: Drive
    private val appFolderName = "SmartArchiveBackup"
    private val backupFileName = "smart_archive_backup.zip"
    private val prefsToBackup = arrayOf("AppCategories", "FileCategoryMapping", "FileHashes", "ThemePrefs")

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_FILE)
        ).setSelectedAccount(account.account)

        drive = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(context.getString(R.string.app_name)).build()
    }

    private suspend fun getAppFolderId(): String? = withContext(Dispatchers.IO) {
        try {
            val query = "mimeType='application/vnd.google-apps.folder' and name='$appFolderName' and trashed=false"
            val result = drive.files().list().setQ(query).setSpaces("drive").setFields("files(id)").execute()
            if (result.files.isNotEmpty()) {
                result.files[0].id
            } else {
                val folderMetadata = DriveFile().apply {
                    name = appFolderName
                    mimeType = "application/vnd.google-apps.folder"
                }
                val createdFolder = drive.files().create(folderMetadata).setFields("id").execute()
                createdFolder.id
            }
        } catch (e: IOException) {
            Log.e("DriveHelper", "Network error getting app folder ID", e)
            null
        } catch (e: Exception) {
            Log.e("DriveHelper", "Unknown error getting app folder ID", e)
            null
        }
    }

    suspend fun getBackupMetadata(): Pair<String, Long>? = withContext(Dispatchers.IO) {
        try {
            val appFolderId = getAppFolderId() ?: return@withContext null
            val query = "'$appFolderId' in parents and name='$backupFileName' and trashed=false"
            val result = drive.files().list().setQ(query).setSpaces("drive").setFields("files(id, modifiedTime)").execute()
            if (result.files.isNotEmpty()) {
                val file = result.files[0]
                Pair(file.id, file.modifiedTime.value)
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e("DriveHelper", "Network error getting backup metadata", e)
            null
        } catch (e: Exception) {
            Log.e("DriveHelper", "Unknown error getting backup metadata", e)
            null
        }
    }

    suspend fun backupData(): Boolean = withContext(Dispatchers.IO) {
        var zipFile: File? = null
        try {
            val appFolderId = getAppFolderId() ?: return@withContext false
            zipFile = createBackupZip() ?: return@withContext false

            val query = "'$appFolderId' in parents and name='$backupFileName' and trashed=false"
            val fileList = drive.files().list().setQ(query).setSpaces("drive").setFields("files(id)").execute()

            val fileMetadata = DriveFile().apply { name = backupFileName }
            val mediaContent = FileContent("application/zip", zipFile)

            if (fileList.files.isEmpty()) {
                fileMetadata.parents = listOf(appFolderId)
                drive.files().create(fileMetadata, mediaContent).execute()
            } else {
                val fileId = fileList.files[0].id
                drive.files().update(fileId, fileMetadata, mediaContent).execute()
            }
            true
        } catch (e: IOException) {
            Log.e("DriveHelper", "Network error during backup", e)
            false
        } catch (e: Exception) {
            Log.e("DriveHelper", "Unknown error during backup", e)
            false
        } finally {
            zipFile?.delete()
        }
    }

    private fun createBackupZip(): File? {
        val zipFile = File(context.cacheDir, backupFileName)
        try {
            if(zipFile.exists()) zipFile.delete()
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val archiveDir = File(context.filesDir, "arsiv")
                if (archiveDir.exists() && archiveDir.isDirectory) {
                    archiveDir.walk().filter { it.isFile }.forEach { file ->
                        val zipPath = "files/arsiv/" + file.relativeTo(archiveDir)
                        addFileToZip(zos, file, zipPath)
                    }
                }
                val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                prefsToBackup.forEach { prefName ->
                    val prefsFile = File(prefsDir, "$prefName.xml")
                    if (prefsFile.exists()) {
                        addFileToZip(zos, prefsFile, "shared_prefs/$prefName.xml")
                    }
                }
            }
            return zipFile
        } catch (e: Exception) {
            Log.e("DriveHelper", "Error creating zip file", e)
            zipFile.delete()
            return null
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, zipPath: String) {
        val entry = ZipEntry(zipPath.replace(File.separatorChar, '/'))
        zos.putNextEntry(entry)
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }

    suspend fun restoreData(): RestoreResult = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "restore_temp.zip")
        val tempRestoreDir = File(context.cacheDir, "restore_temp_dir")
        val backupArsivDir = File(context.filesDir, "arsiv_pre_restore_backup")
        val backupPrefsDir = File(context.cacheDir, "prefs_pre_restore_backup")

        try {
            // Adım 1: Yedeği indir ve aç
            val backupMetadata = getBackupMetadata() ?: return@withContext RestoreResult.NO_BACKUP_FOUND
            val fileId = backupMetadata.first

            if(tempZipFile.exists()) tempZipFile.delete()
            FileOutputStream(tempZipFile).use { fos ->
                drive.files().get(fileId).executeMediaAndDownloadTo(fos)
            }

            if (tempRestoreDir.exists()) tempRestoreDir.deleteRecursively()
            tempRestoreDir.mkdirs()
            unzip(tempZipFile, tempRestoreDir)

            // Adım 2: Mevcut verileri "atomik olarak" değiştirmek için hazırla
            val currentArsivDir = File(context.filesDir, "arsiv")
            val currentPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")

            val restoredArsivDir = File(tempRestoreDir, "files/arsiv")
            val restoredPrefsDir = File(tempRestoreDir, "shared_prefs")

            // Adım 3: Mevcut verileri geçici olarak yedekle (yeniden adlandırarak)
            if (backupArsivDir.exists()) backupArsivDir.deleteRecursively()
            if (backupPrefsDir.exists()) backupPrefsDir.deleteRecursively()

            if (currentArsivDir.exists() && !currentArsivDir.renameTo(backupArsivDir)) {
                return@withContext RestoreResult.RESTORE_FAILED
            }
            if (currentPrefsDir.exists() && !currentPrefsDir.renameTo(backupPrefsDir)) {
                // Eğer arşiv klasörü yedeklemesi başarılı olduysa geri al
                if (backupArsivDir.exists()) backupArsivDir.renameTo(currentArsivDir)
                return@withContext RestoreResult.RESTORE_FAILED
            }


            // Adım 4: Yeni verileri yerine taşı. Hata olursa eski veriler geri yüklenecek.
            try {
                if (restoredArsivDir.exists()) {
                    restoredArsivDir.renameTo(currentArsivDir)
                } else {
                    currentArsivDir.mkdirs() // Yedekte hiç dosya yoksa boş bir klasör oluştur.
                }

                if (restoredPrefsDir.exists()){
                    restoredPrefsDir.renameTo(currentPrefsDir)
                } else {
                    currentPrefsDir.mkdirs() // Yedekte hiç ayar yoksa boş bir klasör oluştur.
                }

                // Her şey yolunda, geçici yedekleri temizle
                if (backupArsivDir.exists()) backupArsivDir.deleteRecursively()
                if (backupPrefsDir.exists()) backupPrefsDir.deleteRecursively()

                return@withContext RestoreResult.SUCCESS
            } catch (e: Exception) {
                Log.e("DriveHelper", "Restore failed while moving new data. Reverting...", e)
                // Hata oluştu! Eski verileri geri yükle.
                if (currentArsivDir.exists()) currentArsivDir.deleteRecursively()
                if (currentPrefsDir.exists()) currentPrefsDir.deleteRecursively()
                if (backupArsivDir.exists()) backupArsivDir.renameTo(currentArsivDir)
                if (backupPrefsDir.exists()) backupPrefsDir.renameTo(currentPrefsDir)
                return@withContext RestoreResult.RESTORE_FAILED
            }
        } catch(e: GoogleJsonResponseException) {
            Log.e("DriveHelper", "Google Drive API error during restore", e)
            return@withContext RestoreResult.DOWNLOAD_FAILED
        } catch (e: IOException) {
            Log.e("DriveHelper", "Network or I/O error during restore", e)
            return@withContext RestoreResult.RESTORE_FAILED
        } catch (e: ZipException) {
            Log.e("DriveHelper", "Backup validation failed: Invalid zip file", e)
            return@withContext RestoreResult.VALIDATION_FAILED
        } catch (e: Exception) {
            Log.e("DriveHelper", "Unknown error during restore", e)
            return@withContext RestoreResult.UNKNOWN_ERROR
        } finally {
            // Geçici dosyaları ve klasörleri her durumda temizle
            if (tempZipFile.exists()) tempZipFile.delete()
            if (tempRestoreDir.exists()) tempRestoreDir.deleteRecursively()
        }
    }
    // YENİ EKLENDİ: Geri yükleme sonrası ayarları tamamlayan fonksiyon
    fun finalizeRestore(context: Context) {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        // Değişiklik: .commit() kullanarak verinin senkronize yazılmasını sağla.
        // Bu tercih, özellikle uygulama yeniden başlatılmadan hemen önce bu ayarların
        // diske yazıldığından emin olmak için kritik ve doğru bir yaklaşımdır.
        prefs.edit()
            .putBoolean("isFirstLaunch", false)
            .putBoolean("has_seen_login_suggestion", true)
            .commit()
    }


    @Throws(IOException::class, ZipException::class)
    private fun unzip(zipFile: File, targetDirectory: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outputFile = File(targetDirectory, entry.name)
                if (!outputFile.canonicalPath.startsWith(targetDirectory.canonicalPath)) {
                    throw SecurityException("Zip Path Traversal Attack detected!")
                }
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    suspend fun deleteBackupData(deleteLocal: Boolean) = withContext(Dispatchers.IO) {
        try {
            if (deleteLocal) {
                cleanLocalData()
            }
            // Drive'daki dosyayı sil
            val appFolderId = getAppFolderId() ?: return@withContext
            val query = "'$appFolderId' in parents and name='$backupFileName' and trashed=false"
            val fileList = drive.files().list().setQ(query).setSpaces("drive").setFields("files(id)").execute()
            if (fileList.files.isNotEmpty()) {
                drive.files().delete(fileList.files[0].id).execute()
            }
        } catch(e: Exception) {
            Log.e("DriveHelper", "Error deleting data", e)
            // Hata durumunu çağıran yere bildirmek için bir sonuç döndürülebilir veya hata fırlatılabilir.
        }
    }

    private fun cleanLocalData() {
        val archiveDir = File(context.filesDir, "arsiv")
        if (archiveDir.exists()) archiveDir.deleteRecursively()

        prefsToBackup.forEach { prefName ->
            context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}