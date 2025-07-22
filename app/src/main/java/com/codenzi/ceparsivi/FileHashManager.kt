package com.codenzi.ceparsivi

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object FileHashManager {

    private const val PREFS_NAME = "FileHashes"

    private var isInvalidated = true
    // Hatanın çözümü için önbelleği daha güvenli bir tiple (String, String) tanımlıyoruz.
    private var hashesCache: MutableMap<String, String>? = null

    fun invalidate() {
        isInvalidated = true
        hashesCache = null
    }

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Önbelleği, tipleri karıştırmayacak şekilde güvenli olarak dolduruyoruz.
    private fun loadHashesIfNeeded(context: Context) {
        if (!isInvalidated && hashesCache != null) return

        val prefs = getPrefs(context)
        val allPrefs = prefs.all
        val tempMap = mutableMapOf<String, String>()
        // Sadece String olan değerleri alarak tip güvenliği sağlıyoruz.
        allPrefs.forEach { (key, value) ->
            if (value is String) {
                tempMap[key] = value
            }
        }
        hashesCache = tempMap
        isInvalidated = false
    }

    fun hashExists(context: Context, hash: String): Boolean {
        loadHashesIfNeeded(context)
        return hashesCache?.containsKey(hash) ?: false
    }

    fun getFileNameForHash(context: Context, hash: String): String? {
        loadHashesIfNeeded(context)
        // Artık tip dönüşümüne gerek yok, kod daha temiz.
        return hashesCache?.get(hash)
    }

    fun addHash(context: Context, hash: String, fileName: String) {
        loadHashesIfNeeded(context)
        getPrefs(context).edit {
            putString(hash, fileName)
        }
        // Önbelleği de anında güncelliyoruz.
        hashesCache?.put(hash, fileName)
    }

    fun removeHashForFile(context: Context, fileName: String) {
        loadHashesIfNeeded(context)
        val prefs = getPrefs(context)
        var hashToRemove: String? = null

        // Artık bu döngü tamamen tip güvenli.
        val allEntries = hashesCache ?: return
        for ((key, value) in allEntries) {
            if (value == fileName) {
                hashToRemove = key
                break
            }
        }

        hashToRemove?.let {
            prefs.edit {
                remove(it)
            }
            hashesCache?.remove(it)
        }
    }

    suspend fun calculateMD5(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val md = MessageDigest.getInstance("MD5")
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } > 0) {
                        md.update(buffer, 0, read)
                    }
                }
                md.digest().joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                Log.e("FileHashManager", "Failed to calculate hash from URI", e)
                null
            }
        }
    }
}