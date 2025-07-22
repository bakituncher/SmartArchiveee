package com.codenzi.ceparsivi

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Kategorileri ve dosya-kategori eşleşmelerini yönetir.
 * Varsayılan kategorileri dil-bağımsız anahtarlarla saklar ve gösterim sırasında çevirir.
 * Tüm "anahtar" mantığı bu sınıfın içinde gizlidir.
 */
object CategoryManager {

    private const val PREFS_CATEGORIES = "AppCategories"
    private const val KEY_CATEGORY_SET = "user_defined_categories_set"

    private const val PREFS_FILE_MAP = "FileCategoryMapping"
    private val gson = Gson()

    private var isInvalidated = true
    private var categoryIdentifiers: MutableSet<String>? = null
    private var fileMapCache: Map<String, String>? = null

    // Varsayılan kategoriler için asla değişmeyecek, dil-bağımsız anahtarlar
    private object CategoryKeys {
        const val OFFICE = "key_office"
        const val IMAGES = "key_images"
        const val VIDEOS = "key_videos"
        const val AUDIO = "key_audio"
        const val ARCHIVES = "key_archives"
        const val OTHER = "key_other"
    }

    // Anahtarları R.string kaynak ID'leri ile eşleştiren harita
    private val defaultCategoryResources = mapOf(
        CategoryKeys.OFFICE to R.string.category_office,
        CategoryKeys.IMAGES to R.string.category_images,
        CategoryKeys.VIDEOS to R.string.category_videos,
        CategoryKeys.AUDIO to R.string.category_audio,
        CategoryKeys.ARCHIVES to R.string.category_archives,
        CategoryKeys.OTHER to R.string.category_other
    )

    fun invalidate() {
        isInvalidated = true
        categoryIdentifiers = null
        fileMapCache = null
    }

    private fun loadCategoriesIfNeeded(context: Context) {
        if (!isInvalidated && categoryIdentifiers != null) return

        val prefs = context.getSharedPreferences(PREFS_CATEGORIES, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_CATEGORY_SET, null)
        categoryIdentifiers = if (savedSet != null) {
            HashSet(savedSet)
        } else {
            val defaultCategoryKeys = defaultCategoryResources.keys
            prefs.edit { putStringSet(KEY_CATEGORY_SET, defaultCategoryKeys) }
            defaultCategoryKeys.toMutableSet()
        }
    }

    private fun loadFileMapIfNeeded(context: Context) {
        if (!isInvalidated && fileMapCache != null) return

        val prefs = context.getSharedPreferences(PREFS_FILE_MAP, Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_FILE_MAP, "{}")
        val type = object : TypeToken<Map<String, String>>() {}.type
        fileMapCache = gson.fromJson(json, type) ?: emptyMap()
    }

    private fun ensureInitialized(context: Context) {
        if (isInvalidated) {
            loadCategoriesIfNeeded(context)
            loadFileMapIfNeeded(context)
            isInvalidated = false
        }
    }

    /**
     * Kullanıcı arayüzünde gösterilecek olan, çevrilmiş ve sıralanmış kategori listesini döndürür.
     * Bu fonksiyon "key_" gibi anahtarları asla dışarı sızdırmaz.
     */
    fun getDisplayCategories(context: Context): List<String> {
        ensureInitialized(context)
        return categoryIdentifiers
            ?.map { identifier -> getTranslatedCategoryName(context, identifier) }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * Yeni bir kategori ekler. Eklemeden önce, aynı isimde bir kategori olup olmadığını kontrol eder.
     */
    fun addCategory(context: Context, newCategoryName: String): Boolean {
        ensureInitialized(context)
        val displayCategories = getDisplayCategories(context)
        if (displayCategories.any { it.equals(newCategoryName, ignoreCase = true) }) {
            return false // Bu isimde bir kategori zaten var.
        }
        categoryIdentifiers?.add(newCategoryName)
        context.getSharedPreferences(PREFS_CATEGORIES, Context.MODE_PRIVATE).edit { putStringSet(KEY_CATEGORY_SET, categoryIdentifiers) }
        return true
    }

    /**
     * Bir kategoriyi siler. Varsayılan kategorilerin silinmesini engeller.
     */
    fun deleteCategory(context: Context, categoryDisplayName: String): Boolean {
        ensureInitialized(context)
        if (isDefaultCategory(context, categoryDisplayName)) return false // Varsayılanlar silinemez.
        if (getFilesInCategory(context, categoryDisplayName).isNotEmpty()) return false // Kategori boş değil.

        val identifier = getIdentifierForDisplayName(context, categoryDisplayName)
        categoryIdentifiers?.remove(identifier)
        context.getSharedPreferences(PREFS_CATEGORIES, Context.MODE_PRIVATE).edit { putStringSet(KEY_CATEGORY_SET, categoryIdentifiers) }
        return true
    }

    /**
     * Bir kategoriyi yeniden adlandırır. Varsayılanların adlandırılmasını engeller.
     */
    fun renameCategory(context: Context, oldDisplayName: String, newDisplayName: String) {
        ensureInitialized(context)
        if (isDefaultCategory(context, oldDisplayName)) return // Varsayılanlar yeniden adlandırılamaz.

        // Yeni isim mevcut bir kategori ismiyle çakışıyor mu kontrol et.
        if (getDisplayCategories(context).any { it.equals(newDisplayName, ignoreCase = true) }) return

        val oldIdentifier = getIdentifierForDisplayName(context, oldDisplayName)
        categoryIdentifiers?.remove(oldIdentifier)
        categoryIdentifiers?.add(newDisplayName)
        context.getSharedPreferences(PREFS_CATEGORIES, Context.MODE_PRIVATE).edit { putStringSet(KEY_CATEGORY_SET, categoryIdentifiers) }

        val fileMap = fileMapCache?.toMutableMap() ?: return
        val updatedMap = fileMap.toMutableMap()
        fileMap.forEach { (filePath, identifier) ->
            if (identifier == oldIdentifier) {
                updatedMap[filePath] = newDisplayName
            }
        }
        saveFileCategoryMap(context, updatedMap)
    }

    /**
     * Dosyaya ait kategorinin çevrilmiş adını döndürür.
     */
    fun getCategoryForFile(context: Context, filePath: String): String? {
        ensureInitialized(context)
        val identifier = fileMapCache?.get(filePath) ?: return null
        return getTranslatedCategoryName(context, identifier)
    }

    /**
     * Bir dosyaya kategori atar. Gösterim adını alır ve arka planda doğru anahtara çevirir.
     */
    fun setCategoryForFile(context: Context, filePath: String, categoryDisplayName: String) {
        ensureInitialized(context)
        val fileMap = fileMapCache?.toMutableMap() ?: mutableMapOf()
        val identifier = getIdentifierForDisplayName(context, categoryDisplayName)
        fileMap[filePath] = identifier
        saveFileCategoryMap(context, fileMap)
    }

    fun removeCategoryForFile(context: Context, filePath: String) {
        ensureInitialized(context)
        val fileMap = fileMapCache?.toMutableMap() ?: return
        fileMap.remove(filePath)
        saveFileCategoryMap(context, fileMap)
    }

    fun getFilesInCategory(context: Context, categoryDisplayName: String): List<String> {
        ensureInitialized(context)
        val identifier = getIdentifierForDisplayName(context, categoryDisplayName)
        return fileMapCache?.filter { it.value == identifier }?.keys?.toList() ?: emptyList()
    }

    fun getDefaultCategoryNameByExtension(context: Context, fileName: String): String {
        val key = when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt" -> CategoryKeys.OFFICE
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> CategoryKeys.IMAGES
            "mp4", "mkv", "avi", "mov", "3gp", "webm" -> CategoryKeys.VIDEOS
            "mp3", "wav", "m4a", "aac", "flac", "ogg" -> CategoryKeys.AUDIO
            "zip", "rar", "7z", "tar", "gz" -> CategoryKeys.ARCHIVES
            else -> CategoryKeys.OTHER
        }
        return getTranslatedCategoryName(context, key)
    }

    // Dahili kullanım için: Bir anahtarı alır, çevrilmiş adını döndürür.
    private fun getTranslatedCategoryName(context: Context, identifier: String): String {
        return defaultCategoryResources[identifier]?.let { context.getString(it) } ?: identifier
    }

    // Dahili kullanım için: Çevrilmiş bir adı alır, arka plandaki anahtarı veya adı döndürür.
    private fun getIdentifierForDisplayName(context: Context, displayName: String): String {
        // Önce varsayılan kategoriler içinde ara
        defaultCategoryResources.forEach { (key, resourceId) ->
            if (context.getString(resourceId).equals(displayName, ignoreCase = true)) {
                return key
            }
        }
        // Bulunamazsa, bu bir özel kategoridir
        return displayName
    }

    /**
     * Bir gösterim adının varsayılan bir kategoriye ait olup olmadığını kontrol eder.
     */
    fun isDefaultCategory(context: Context, displayName: String): Boolean {
        return defaultCategoryResources.values.any { context.getString(it).equals(displayName, ignoreCase = true) }
    }

    private fun saveFileCategoryMap(context: Context, map: Map<String, String>) {
        val json = gson.toJson(map)
        context.getSharedPreferences(PREFS_FILE_MAP, Context.MODE_PRIVATE).edit { putString(PREFS_FILE_MAP, json) }
        fileMapCache = map
    }
}