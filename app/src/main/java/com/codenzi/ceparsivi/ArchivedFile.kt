package com.codenzi.ceparsivi

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ArchivedFile(
    val fileName: String,
    val filePath: String,
    val dateAdded: String,
    var category: String, // Değişiklik: Artık String olarak tutuluyor.
    val size: Long
) : Parcelable