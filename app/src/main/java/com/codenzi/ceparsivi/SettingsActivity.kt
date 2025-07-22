package com.codenzi.ceparsivi

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.codenzi.ceparsivi.databinding.ActivitySettingsBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.services.drive.DriveScopes
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.codenzi.ceparsivi.BuildConfig

class SettingsActivity : AppCompatActivity(), CategoryEntryDialogFragment.CategoryDialogListener {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private var driveHelper: GoogleDriveHelper? = null
    private lateinit var billingManager: BillingManager
    private var selectedPlanId: String = BillingManager.YEARLY_SKU

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task)
        } else {
            Toast.makeText(this, getString(R.string.sign_in_failed), Toast.LENGTH_SHORT).show()
            setBackupButtonsEnabled(false)
        }
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show()
                binding.switchAutoBackup.isChecked = false
            }
        }

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                exportUserData()
            } else {
                Toast.makeText(this, "Storage permission is required to save the file.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupGoogleSignIn()
        loadSettings()
        setupPrivacyOptionsButton()
        setupBilling()
        setupPlanSelectionListeners()

        binding.textViewTheme.setOnClickListener { showThemeSelectionDialog() }
        binding.textViewManageCategories.setOnClickListener { showManageCategoriesDialog() }
        binding.textViewPrivacyPolicy.setOnClickListener { openPrivacyPolicyLink() }
        binding.textViewTermsOfUse.setOnClickListener { openTermsOfUseLink() }
        binding.textViewContactUs.setOnClickListener { openContactUsEmail() }
        binding.textViewDownloadData.setOnClickListener { exportUserData() }


        binding.buttonDriveSignInOut.setOnClickListener {
            if (GoogleSignIn.getLastSignedInAccount(this) == null) signIn() else signOut()
        }
        binding.buttonBackup.setOnClickListener { backupData() }
        binding.buttonRestore.setOnClickListener { restoreData() }
        binding.buttonDeleteBackup.setOnClickListener { showDeleteOptionsDialog() }
        binding.switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            handleAutoBackupSwitch(isChecked)
        }
    }

    private fun setupBilling() {
        billingManager = BillingManager(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        lifecycleScope.launch {
            billingManager.isPremium.collectLatest { isPremium ->
                updatePremiumUI(isPremium)
            }
        }
        lifecycleScope.launch {
            billingManager.productDetails.collectLatest { products ->
                val monthlyDetails = products[BillingManager.MONTHLY_SKU]
                val yearlyDetails = products[BillingManager.YEARLY_SKU]

                monthlyDetails?.let {
                    val price = it.subscriptionOfferDetails?.first()?.pricingPhases?.pricingPhaseList?.first()?.formattedPrice ?: ""
                    binding.textMonthlyPrice.text = getString(R.string.premium_price_format_monthly, price)
                }
                yearlyDetails?.let {
                    val price = it.subscriptionOfferDetails?.first()?.pricingPhases?.pricingPhaseList?.first()?.formattedPrice ?: ""
                    binding.textYearlyPrice.text = getString(R.string.premium_price_format_yearly, price)
                }

                if (monthlyDetails != null && yearlyDetails != null) {
                    try {
                        val monthlyPriceMicros = monthlyDetails.subscriptionOfferDetails?.first()?.pricingPhases?.pricingPhaseList?.first()?.priceAmountMicros ?: 0L
                        val yearlyPriceMicros = yearlyDetails.subscriptionOfferDetails?.first()?.pricingPhases?.pricingPhaseList?.first()?.priceAmountMicros ?: 0L

                        if (monthlyPriceMicros > 0 && yearlyPriceMicros > 0) {
                            val totalMonthlyForYear = monthlyPriceMicros * 12
                            val savings = totalMonthlyForYear - yearlyPriceMicros
                            val savingsPercentage = (savings.toDouble() / totalMonthlyForYear.toDouble() * 100).toInt()

                            if (savingsPercentage > 0) {
                                binding.badgeYearlyDeal.text = getString(R.string.premium_yearly_deal_badge, savingsPercentage)
                                binding.badgeYearlyDeal.visibility = View.VISIBLE
                            } else {
                                binding.badgeYearlyDeal.visibility = View.GONE
                            }
                        }
                    } catch (e: Exception) {
                        binding.badgeYearlyDeal.visibility = View.GONE
                        Log.e("SettingsActivity", "Tasarruf oranı hesaplanamadı.", e)
                    }
                }
            }
        }
    }

    private fun setupPlanSelectionListeners() {
        binding.cardMonthlyPlan.setOnClickListener { selectPlan(BillingManager.MONTHLY_SKU) }
        binding.cardYearlyPlan.setOnClickListener { selectPlan(BillingManager.YEARLY_SKU) }
        binding.buttonUpgradeToPremium.setOnClickListener {
            billingManager.launchPurchaseFlow(this, selectedPlanId)
        }
    }

    private fun selectPlan(planId: String) {
        selectedPlanId = planId
        val selectedStrokeWidth = (2 * resources.displayMetrics.density).toInt()
        val defaultStrokeWidth = (1 * resources.displayMetrics.density).toInt()

        binding.cardMonthlyPlan.strokeColor = ContextCompat.getColor(this, if (planId == BillingManager.MONTHLY_SKU) R.color.primary else R.color.outline)
        binding.cardMonthlyPlan.strokeWidth = if (planId == BillingManager.MONTHLY_SKU) selectedStrokeWidth else defaultStrokeWidth

        binding.cardYearlyPlan.strokeColor = ContextCompat.getColor(this, if (planId == BillingManager.YEARLY_SKU) R.color.primary else R.color.outline)
        binding.cardYearlyPlan.strokeWidth = if (planId == BillingManager.YEARLY_SKU) selectedStrokeWidth else defaultStrokeWidth
    }

    private fun updatePremiumUI(isPremium: Boolean) {
        if (isPremium) {
            binding.premiumIconHeader.setImageResource(R.drawable.ic_cloud_done)
            binding.premiumCardTitle.text = getString(R.string.premium_thanks_title)
            binding.premiumCardDescription.text = getString(R.string.premium_thanks_description)
            binding.premiumPlansContainer.visibility = View.GONE
            binding.premiumFeaturesList.visibility = View.GONE
            binding.buttonUpgradeToPremium.visibility = View.GONE
        } else {
            selectPlan(BillingManager.YEARLY_SKU)
            binding.premiumPlansContainer.visibility = View.VISIBLE
            binding.premiumFeaturesList.visibility = View.VISIBLE
            binding.buttonUpgradeToPremium.visibility = View.VISIBLE
            binding.premiumIconHeader.setImageResource(R.drawable.ic_intro_welcome)
            binding.premiumCardTitle.text = getString(R.string.premium_main_title)
            binding.premiumCardDescription.text = getString(R.string.premium_main_description)
        }
    }

    override fun onResume() {
        super.onResume()
        billingManager.queryPurchases()
    }

    private fun openContactUsEmail() {
        val emailAddress = getString(R.string.contact_us_email)
        val subject = getString(R.string.contact_us_subject)

        val mailtoUri = Uri.parse("mailto:$emailAddress?subject=${Uri.encode(subject)}")
        val intent = Intent(Intent.ACTION_SENDTO, mailtoUri)

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.no_email_client), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTermsOfUseLink() {
        val termsUrl = getString(R.string.terms_of_use_url)
        val intent = Intent(Intent.ACTION_VIEW, termsUrl.toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.error_no_app_for_action), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPrivacyPolicyLink() {
        val privacyPolicyUrl = getString(R.string.privacy_policy_url)
        val intent = Intent(Intent.ACTION_VIEW, privacyPolicyUrl.toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.error_no_app_for_action), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPrivacyOptionsButton() {
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)

        // Butonun görünürlüğünü rıza gerekliliğine göre ayarla
        if (consentInformation.privacyOptionsRequirementStatus == com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            binding.textViewPrivacySettings.visibility = View.VISIBLE
            binding.dividerPrivacySettings.visibility = View.VISIBLE

            // Butona tıklandığında rıza formunu tekrar göster
            binding.textViewPrivacySettings.setOnClickListener {
                UserMessagingPlatform.showPrivacyOptionsForm(this) { formError ->
                    if (formError != null) {
                        Log.w("SettingsActivity", "Gizlilik seçenekleri formu hatası: ${formError.message}")
                        Toast.makeText(this, getString(R.string.privacy_options_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            binding.textViewPrivacySettings.visibility = View.GONE
            binding.dividerPrivacySettings.visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        // Aktivite başladığında mevcut hesabı kontrol et.
        // updateUI fonksiyonu, hesap null ise sessiz girişi deneyecek.
        updateUI(GoogleSignIn.getLastSignedInAccount(this))
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        binding.switchAutoBackup.isChecked = prefs.getBoolean("auto_backup_enabled", false)
    }

    private fun handleAutoBackupSwitch(isEnabled: Boolean) {
        if (isEnabled) {
            val account = GoogleSignIn.getLastSignedInAccount(this)
            if (account == null) {
                Toast.makeText(this, getString(R.string.auto_backup_sign_in_required), Toast.LENGTH_LONG).show()
                binding.switchAutoBackup.isChecked = false
                return
            }
            checkAndRequestNotificationPermission()
            BackupScheduler.schedulePeriodicBackup(this)
            Toast.makeText(this, getString(R.string.auto_backup_enabled_toast), Toast.LENGTH_SHORT).show()
        } else {
            BackupScheduler.cancelPeriodicBackup(this)
            Toast.makeText(this, getString(R.string.auto_backup_disabled_toast), Toast.LENGTH_SHORT).show()
        }
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit {
            putBoolean("auto_backup_enabled", isEnabled)
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {}
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.notification_permission_title))
                        .setMessage(getString(R.string.notification_permission_rationale))
                        .setPositiveButton(getString(R.string.ok)) { _, _ ->
                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            // DİKKAT: Kimlik artık BuildConfig'dan alınıyor.
            .requestServerAuthCode(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun signIn() {
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener(this) {
            updateUI(null)
            Toast.makeText(this, getString(R.string.sign_out_success), Toast.LENGTH_SHORT).show()
            if (binding.switchAutoBackup.isChecked) {
                binding.switchAutoBackup.isChecked = false
            }
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)!!
            Toast.makeText(this, getString(R.string.welcome_message, account.displayName), Toast.LENGTH_SHORT).show()
            updateUI(account)
            checkBackupAndPrompt(account)
        } catch (e: ApiException) {
            Log.w("SignIn", "signInResult:failed code=" + e.statusCode)
            updateUI(null)
        }
    }

    private fun updateUI(account: GoogleSignInAccount?) {
        if (account != null) {
            // --- HESAP BULUNDU: Her şey normal ---
            binding.textViewDriveStatus.text = getString(R.string.status_signed_in_as, account.email)
            binding.buttonDriveSignInOut.text = getString(R.string.action_sign_out)
            setBackupButtonsEnabled(true)
            binding.switchAutoBackup.isEnabled = true
            binding.textViewLastBackup.visibility = View.VISIBLE // Son yedekleme bilgisi için görünür yap
            driveHelper = GoogleDriveHelper(this, account)
            checkLastBackup()
        } else {
            // --- HESAP BULUNAMADI (Restore sonrası gibi): Sessiz girişi dene ---
            // Önce arayüzü "Kontrol ediliyor..." durumuna getir
            binding.textViewDriveStatus.text = getString(R.string.last_backup_checking) // "Yedek kontrol ediliyor" metnini kullanabiliriz
            binding.buttonDriveSignInOut.text = getString(R.string.action_sign_in)
            binding.textViewLastBackup.visibility = View.GONE // Henüz yedek bilgisi yok
            setBackupButtonsEnabled(false)
            binding.switchAutoBackup.isEnabled = false
            driveHelper = null

            // Arka planda sessiz giriş yapmayı dene
            googleSignInClient.silentSignIn().addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sessiz giriş başarılı! UI'ı yeni hesapla tekrar güncelle.
                    val signedInAccount = task.result
                    updateUI(signedInAccount)
                } else {
                    // Sessiz giriş başarısız oldu, kullanıcı gerçekten giriş yapmamış.
                    // Arayüzü "Giriş yapılmadı" olarak ayarla.
                    binding.textViewDriveStatus.text = getString(R.string.status_not_signed_in)
                }
            }
        }
    }
    private fun setBackupButtonsEnabled(isEnabled: Boolean) {
        binding.buttonBackup.isEnabled = isEnabled
        binding.buttonRestore.isEnabled = isEnabled
        binding.buttonDeleteBackup.isEnabled = isEnabled
    }

    private fun checkLastBackup() {
        binding.textViewLastBackup.text = getString(R.string.last_backup_checking)
        binding.textViewLastBackup.visibility = View.VISIBLE
        lifecycleScope.launch {
            val backupMetadata = withContext(Dispatchers.IO) { driveHelper?.getBackupMetadata() }
            binding.textViewLastBackup.text = if (backupMetadata != null) {
                val formattedDate = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(backupMetadata.second))
                getString(R.string.last_backup_at, formattedDate)
            } else {
                getString(R.string.last_backup_never)
            }
        }
    }

    private fun checkBackupAndPrompt(account: GoogleSignInAccount) {
        val localDriveHelper = GoogleDriveHelper(this, account)
        lifecycleScope.launch(Dispatchers.IO) {
            val backupMetadata = localDriveHelper.getBackupMetadata() ?: return@launch
            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            val lastPromptedTimestamp = prefs.getLong("last_prompted_timestamp_${account.id}", 0L)
            if (backupMetadata.second > lastPromptedTimestamp) {
                withContext(Dispatchers.Main) {
                    showRestorePromptDialog(backupMetadata.second, account.id!!)
                }
            }
        }
    }

    private fun showRestorePromptDialog(backupTimestamp: Long, accountId: String) {
        val formattedDate = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(backupTimestamp))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.backup_found_title))
            .setMessage(getString(R.string.backup_found_message, formattedDate))
            .setPositiveButton(getString(R.string.action_restore_backup)) { _, _ -> performRestore() }
            .setNegativeButton(getString(R.string.cancel), null)
            .setOnDismissListener {
                val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                prefs.edit { putLong("last_prompted_timestamp_${accountId}", backupTimestamp) }
            }
            .show()
    }

    private fun backupData() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.backup_starting_title))
            .setMessage(getString(R.string.backup_starting_message))
            .setCancelable(false)
            .show()
        setBackupButtonsEnabled(false)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { driveHelper?.backupData() }
            dialog.dismiss()
            setBackupButtonsEnabled(true)
            if (result == true) {
                Toast.makeText(this@SettingsActivity, getString(R.string.backup_success), Toast.LENGTH_LONG).show()
                checkLastBackup()
            } else {
                Toast.makeText(this@SettingsActivity, getString(R.string.backup_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun restoreData() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore_data_title))
            .setMessage(getString(R.string.restore_data_message))
            .setPositiveButton(getString(R.string.restore_data_confirm)) { _, _ -> performRestore() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performRestore() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.restoring_dialog_title))
            .setMessage(getString(R.string.restoring_dialog_message))
            .setCancelable(false)
            .show()
        setBackupButtonsEnabled(false)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { driveHelper?.restoreData() }
            dialog.dismiss()
            setBackupButtonsEnabled(true)
            when (result) {
                RestoreResult.SUCCESS -> {
                    driveHelper?.finalizeRestore(applicationContext)
                    CategoryManager.invalidate()
                    FileHashManager.invalidate()
                    Toast.makeText(this@SettingsActivity, getString(R.string.restore_success_restarting), Toast.LENGTH_LONG).show()
                    restartApp()
                }
                RestoreResult.NO_BACKUP_FOUND -> showRestoreError(getString(R.string.restore_error_no_backup))
                RestoreResult.DOWNLOAD_FAILED -> showRestoreError(getString(R.string.restore_error_download))
                RestoreResult.VALIDATION_FAILED -> showRestoreError(getString(R.string.restore_error_validation))
                RestoreResult.RESTORE_FAILED -> showRestoreError(getString(R.string.restore_error_generic))
                else -> showRestoreError(getString(R.string.restore_error_unknown))
            }
        }
    }

    private fun showRestoreError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore_failed_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val mainIntent = Intent.makeRestartActivityTask(intent!!.component)
        startActivity(mainIntent)
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    private fun showDeleteOptionsDialog() {
        val options = arrayOf(
            getString(R.string.action_delete_drive_only),
            getString(R.string.action_delete_drive_and_device)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_delete_drive_data))
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showFinalDeleteConfirmationDialog(deleteLocal = false) // Sadece Drive
                    1 -> showFinalDeleteConfirmationDialog(deleteLocal = true)  // Drive ve Cihaz
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showFinalDeleteConfirmationDialog(deleteLocal: Boolean) {
        val title: String
        val message: String
        val confirmButtonText: String

        if (deleteLocal) {
            title = getString(R.string.delete_all_data_title)
            message = getString(R.string.delete_all_data_message)
            confirmButtonText = getString(R.string.delete_all_data_confirm)
        } else {
            title = getString(R.string.action_delete_drive_only)
            message = getString(R.string.delete_drive_only_confirmation_message)
            confirmButtonText = getString(R.string.action_delete)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(confirmButtonText) { _, _ ->
                performDelete(deleteLocal)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performDelete(deleteLocal: Boolean) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.deleting_data_title))
            .setMessage(getString(R.string.deleting_data_message))
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                driveHelper?.deleteBackupData(deleteLocal)
            }
            dialog.dismiss()

            if (deleteLocal) {
                // Hem cihaz hem Drive silindiyse
                CategoryManager.invalidate()
                FileHashManager.invalidate()
                Toast.makeText(this@SettingsActivity, getString(R.string.delete_data_success), Toast.LENGTH_LONG).show()
                restartApp()
            } else {
                // Sadece Drive silindiyse
                Toast.makeText(this@SettingsActivity, getString(R.string.delete_drive_data_success), Toast.LENGTH_LONG).show()
                checkLastBackup() // Yedek durumunu tekrar kontrol et
            }
        }
    }

    private fun exportUserData() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.exporting_data_title))
            .setMessage(getString(R.string.exporting_data_message))
            .setCancelable(false)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            var tempZipFile: File? = null
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "smart_archive_export_$timeStamp.zip"
                tempZipFile = File(cacheDir, "temp_export.zip")
                if (tempZipFile.exists()) tempZipFile.delete()

                val prefsToBackup = arrayOf("AppCategories", "FileCategoryMapping", "FileHashes", "ThemePrefs")

                ZipOutputStream(FileOutputStream(tempZipFile)).use { zos ->
                    val archiveDir = File(filesDir, "arsiv")
                    if (archiveDir.exists() && archiveDir.isDirectory) {
                        archiveDir.walk().filter { it.isFile }.forEach { file ->
                            val zipPath = "files/arsiv/" + file.relativeTo(archiveDir)
                            val entry = ZipEntry(zipPath.replace(File.separatorChar, '/'))
                            zos.putNextEntry(entry)
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                    val prefsDir = File(applicationInfo.dataDir, "shared_prefs")
                    prefsToBackup.forEach { prefName ->
                        val prefsFile = File(prefsDir, "$prefName.xml")
                        if (prefsFile.exists()) {
                            val zipPath = "shared_prefs/$prefName.xml"
                            val entry = ZipEntry(zipPath)
                            zos.putNextEntry(entry)
                            prefsFile.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            tempZipFile.inputStream().use { it.copyTo(outputStream) }
                        }
                    } else {
                        throw IOException("Failed to create new MediaStore entry for API 29+.")
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val destinationFile = File(downloadsDir, fileName)
                    FileOutputStream(destinationFile).use { outputStream ->
                        tempZipFile.inputStream().use { it.copyTo(outputStream) }
                    }
                }

                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@SettingsActivity, getString(R.string.download_data_success, fileName), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("ExportData", "Error exporting user data", e)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@SettingsActivity, getString(R.string.download_data_error), Toast.LENGTH_LONG).show()
                }
            } finally {
                tempZipFile?.delete()
            }
        }
    }

    override fun onCategorySaved(newName: String, oldName: String?) {
        if (oldName == null) {
            if (!CategoryManager.addCategory(this, newName)) {
                Toast.makeText(this, getString(R.string.category_already_exists), Toast.LENGTH_SHORT).show()
            }
        } else {
            CategoryManager.renameCategory(this, oldName, newName)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showThemeSelectionDialog() {
        val themes = arrayOf(getString(R.string.theme_light), getString(R.string.theme_dark), getString(R.string.theme_system))
        val themeModes = arrayOf(ThemeManager.ThemeMode.LIGHT, ThemeManager.ThemeMode.DARK, ThemeManager.ThemeMode.SYSTEM)
        val currentThemeValue = ThemeManager.getTheme(this)
        val checkedItem = themeModes.indexOfFirst { it.value == currentThemeValue }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_theme))
            .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                val selectedThemeMode = themeModes[which]
                if (currentThemeValue != selectedThemeMode.value) {
                    ThemeManager.setTheme(this, selectedThemeMode)
                    ThemeManager.applyTheme(selectedThemeMode.value)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showManageCategoriesDialog() {
        val categories = CategoryManager.getDisplayCategories(this).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.manage_categories))
            .setItems(categories) { _, which ->
                showCategoryActionsDialog(categories[which])
            }
            .setPositiveButton(getString(R.string.add_new_category)) { _, _ ->
                showAddOrEditCategoryDialog(null)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showCategoryActionsDialog(categoryName: String) {
        val actions = mutableListOf(getString(R.string.action_rename))
        if (!CategoryManager.isDefaultCategory(this, categoryName)) {
            actions.add(getString(R.string.action_delete))
        }

        AlertDialog.Builder(this)
            .setTitle(categoryName)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    getString(R.string.action_rename) -> showAddOrEditCategoryDialog(categoryName)
                    getString(R.string.action_delete) -> showDeleteCategoryConfirmationDialog(categoryName)
                }
            }
            .show()
    }

    private fun showAddOrEditCategoryDialog(existingCategory: String?) {
        val dialog = CategoryEntryDialogFragment.newInstance(existingCategory)
        dialog.listener = this
        dialog.show(supportFragmentManager, "CategoryEntryDialog")
    }

    private fun showDeleteCategoryConfirmationDialog(categoryName: String) {
        val filesInCategory = CategoryManager.getFilesInCategory(this, categoryName)
        if (filesInCategory.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.cannot_delete_category_title))
                .setMessage(getString(R.string.cannot_delete_category_message, filesInCategory.size, categoryName))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_category_confirmation_title, categoryName))
            .setMessage(getString(R.string.delete_category_confirmation_message))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                CategoryManager.deleteCategory(this, categoryName)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}