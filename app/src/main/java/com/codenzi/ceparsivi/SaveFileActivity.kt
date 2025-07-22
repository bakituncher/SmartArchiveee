package com.codenzi.ceparsivi

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.codenzi.ceparsivi.databinding.DialogSaveFileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SaveFileActivity : AppCompatActivity(), CategoryEntryDialogFragment.CategoryDialogListener {

    private lateinit var binding: DialogSaveFileBinding
    private lateinit var categoryAdapter: ArrayAdapter<String>

    private lateinit var addNewCategoryString: String
    private lateinit var selectCategoryString: String
    private var originalFileNameForCategory: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogSaveFileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fileUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        if (intent?.action == Intent.ACTION_SEND && fileUri != null) {
            setupUI(fileUri)
        } else {
            Toast.makeText(this, R.string.file_not_received, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupUI(fileUri: Uri) {
        originalFileNameForCategory = getFileName(fileUri)
        val fileNameWithoutExtension = originalFileNameForCategory.substringBeforeLast('.', originalFileNameForCategory)
        val fileExtension = originalFileNameForCategory.substringAfterLast('.', "")

        binding.editTextFileName.setText(fileNameWithoutExtension)

        setupCategoryDropdown()
        setupPreview(binding, fileUri, fileExtension)
        setupButtons(fileUri, fileExtension)
    }

    override fun onCategorySaved(newName: String, oldName: String?) {
        if (CategoryManager.addCategory(this, newName)) {
            updateCategoryDropdown()
            binding.autoCompleteCategory.setText(newName, false)
        } else {
            Toast.makeText(this, getString(R.string.category_already_exists), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtons(fileUri: Uri, fileExtension: String) {
        binding.buttonCancel.setOnClickListener {
            finishWithResult(Activity.RESULT_CANCELED)
        }

        binding.buttonSave.setOnClickListener {
            val newBaseName = binding.editTextFileName.text.toString().trim()
            val selectedItem = binding.autoCompleteCategory.text.toString()
            val finalCategory: String

            if (newBaseName.isBlank()) {
                binding.textInputLayout.error = getString(R.string.error_invalid_name)
                return@setOnClickListener
            } else {
                binding.textInputLayout.error = null
            }

            finalCategory = if (selectedItem.isBlank() || selectedItem == selectCategoryString) {
                CategoryManager.getDefaultCategoryNameByExtension(this, originalFileNameForCategory)
            } else {
                selectedItem
            }

            val newName = if (fileExtension.isNotEmpty()) "$newBaseName.$fileExtension" else newBaseName
            processSaveRequest(fileUri, newName, finalCategory)
        }
    }

    private fun setupCategoryDropdown() {
        addNewCategoryString = getString(R.string.add_new_category_spinner)
        selectCategoryString = getString(R.string.select_a_category)

        categoryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        binding.autoCompleteCategory.setAdapter(categoryAdapter)

        updateCategoryDropdown()

        binding.autoCompleteCategory.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position).toString()
            if (selected == addNewCategoryString) {
                val dialog = CategoryEntryDialogFragment.newInstance(null)
                dialog.listener = this@SaveFileActivity
                dialog.show(supportFragmentManager, "CategoryEntryDialog")
                binding.autoCompleteCategory.setText("", false)
            }
        }
    }

    private fun updateCategoryDropdown() {
        // Sadece kullanıcı tarafından oluşturulmuş kategorileri al
        val userCategories = CategoryManager.getDisplayCategories(this)
            .filter { !CategoryManager.isDefaultCategory(this, it) }
            .sorted()

        val dropdownList = mutableListOf(selectCategoryString)
        dropdownList.addAll(userCategories)
        dropdownList.add(addNewCategoryString)

        categoryAdapter.clear()
        categoryAdapter.addAll(dropdownList)
        categoryAdapter.notifyDataSetChanged()
    }

    private fun processSaveRequest(uri: Uri, newName: String, category: String) {
        lifecycleScope.launch {
            setButtonsEnabled(false)

            val hash = FileHashManager.calculateMD5(this@SaveFileActivity, uri)
            if (hash != null && FileHashManager.hashExists(this@SaveFileActivity, hash)) {
                val existingFileName = FileHashManager.getFileNameForHash(this@SaveFileActivity, hash) ?: newName
                Toast.makeText(this@SaveFileActivity, getString(R.string.this_file_already_exists, existingFileName), Toast.LENGTH_LONG).show()
                finishWithResult(Activity.RESULT_CANCELED)
                return@launch
            }

            val outputFile = File(filesDir, "arsiv/$newName")
            if (outputFile.exists()) {
                Toast.makeText(this@SaveFileActivity, getString(R.string.file_already_exists_with_name, newName), Toast.LENGTH_LONG).show()
                setButtonsEnabled(true)
                return@launch
            }

            saveFileAndFinish(uri, newName, category, hash)
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.buttonSave.isEnabled = enabled
        binding.buttonCancel.isEnabled = enabled
    }

    private fun saveFileAndFinish(uri: Uri, newName: String, category: String, hash: String?) {
        lifecycleScope.launch {
            val success = copyFileToInternalStorage(uri, newName)
            withContext(Dispatchers.Main) {
                if (success) {
                    val filePath = File(filesDir, "arsiv/$newName").absolutePath
                    hash?.let { FileHashManager.addHash(applicationContext, it, newName) }
                    CategoryManager.setCategoryForFile(applicationContext, filePath, category)
                    Toast.makeText(applicationContext, getString(R.string.file_saved_as, newName), Toast.LENGTH_LONG).show()
                    finishWithResult(Activity.RESULT_OK)
                } else {
                    Toast.makeText(applicationContext, getString(R.string.error_file_not_saved), Toast.LENGTH_LONG).show()
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun setupPreview(binding: DialogSaveFileBinding, uri: Uri, extension: String) {
        val mimeType = contentResolver.getType(uri) ?: ""
        val previewCard = binding.previewCard
        val imageViewPreview = binding.imageViewPreview

        previewCard.isVisible = true

        when {
            mimeType.startsWith("image/") -> {
                imageViewPreview.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(this).load(uri).into(imageViewPreview)
            }

            mimeType.startsWith("video/") -> {
                imageViewPreview.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(this).load(uri).placeholder(R.drawable.ic_file_video).into(imageViewPreview)
            }

            extension.equals("pdf", ignoreCase = true) -> {
                imageViewPreview.scaleType = ImageView.ScaleType.FIT_CENTER
                lifecycleScope.launch {
                    val bitmap = renderPdfThumbnail(uri)
                    if (bitmap != null) {
                        imageViewPreview.setImageBitmap(bitmap)
                    } else {
                        imageViewPreview.setImageResource(R.drawable.ic_file_pdf)
                    }
                }
            }

            else -> {
                imageViewPreview.scaleType = ImageView.ScaleType.CENTER_INSIDE
                val iconRes = getIconForDefaultCategory(CategoryManager.getDefaultCategoryNameByExtension(this, getFileName(uri)), extension)
                imageViewPreview.setImageResource(iconRes)
            }
        }
    }

    private suspend fun renderPdfThumbnail(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val bitmap = createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return@withContext bitmap
                    }
                }
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e("SaveFileActivity", "PDF önizlemesi oluşturulamadı", e)
            return@withContext null
        }
    }

    private suspend fun copyFileToInternalStorage(uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val outputDir = File(filesDir, "arsiv")
                if (!outputDir.exists()) outputDir.mkdirs()
                FileOutputStream(File(outputDir, newName)).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("SaveFileActivity", "Dosya kopyalanamadı", e)
            false
        }
    }

    private fun getFileName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val colIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (colIndex >= 0) return it.getString(colIndex)
            }
        }
        return uri.path?.let { File(it).name } ?: getString(R.string.default_file_name, System.currentTimeMillis())
    }

    private fun finishWithResult(resultCode: Int) {
        setResult(resultCode)
        finish()
    }

    private fun getIconForDefaultCategory(categoryName: String, fileName: String): Int {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (categoryName == getString(R.string.category_office)) {
            return when (extension) {
                "pdf" -> R.drawable.ic_file_pdf
                "doc", "docx" -> R.drawable.ic_file_doc
                "ppt", "pptx" -> R.drawable.ic_file_doc
                else -> R.drawable.ic_file_generic
            }
        }
        return when (categoryName) {
            getString(R.string.category_images) -> R.drawable.ic_file_image
            getString(R.string.category_videos) -> R.drawable.ic_file_video
            getString(R.string.category_audio) -> R.drawable.ic_file_audio
            getString(R.string.category_archives) -> R.drawable.ic_file_archive
            else -> R.drawable.ic_file_generic
        }
    }
}