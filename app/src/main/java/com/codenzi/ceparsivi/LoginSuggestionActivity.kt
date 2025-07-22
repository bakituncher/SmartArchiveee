// Konum: app/src/main/java/com/codenzi/ceparsivi/LoginSuggestionActivity.kt

package com.codenzi.ceparsivi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.codenzi.ceparsivi.databinding.ActivityLoginSuggestionBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.codenzi.ceparsivi.BuildConfig

class LoginSuggestionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginSuggestionBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private var driveHelper: GoogleDriveHelper? = null

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task)
        } else {
            setLoading(false)
            Toast.makeText(this, getString(R.string.sign_in_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginSuggestionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        setupTermsAndConditions()
    }

    private fun setupTermsAndConditions() {
        binding.termsTextView.movementMethod = LinkMovementMethod.getInstance()
        updateButtonState(false)
        binding.termsCheckboxControl.setOnCheckedChangeListener { _, isChecked ->
            updateButtonState(isChecked)
        }
        binding.termsTextView.setOnClickListener {
            binding.termsCheckboxControl.toggle()
        }
        binding.buttonSignIn.setOnClickListener { checkActionButtons { signIn() } }
        binding.textMaybeLater.setOnClickListener { checkActionButtons { navigateToMain(markAsSeen = true) } }
    }

    private fun updateButtonState(isEnabled: Boolean) {
        binding.buttonSignIn.isEnabled = isEnabled
        binding.textMaybeLater.isEnabled = isEnabled
        binding.buttonSignIn.alpha = if (isEnabled) 1.0f else 0.5f
        binding.textMaybeLater.alpha = if (isEnabled) 1.0f else 0.5f
    }

    private fun checkActionButtons(action: () -> Unit) {
        if (binding.termsCheckboxControl.isChecked) {
            action()
        } else {
            Toast.makeText(this, getString(R.string.accept_terms_to_continue), Toast.LENGTH_SHORT).show()
        }
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.contentGroup.visibility = if (isLoading) View.GONE else View.VISIBLE
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

    private fun handleSignInResult(completedTask: com.google.android.gms.tasks.Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)!!
            Toast.makeText(this, getString(R.string.welcome_message, account.displayName), Toast.LENGTH_SHORT).show()
            driveHelper = GoogleDriveHelper(this, account)
            checkBackupAndPrompt(account)
        } catch (e: ApiException) {
            setLoading(false)
            Toast.makeText(this, getString(R.string.sign_in_failed_with_error, e.statusCode), Toast.LENGTH_LONG).show()
        }
    }

    private fun checkBackupAndPrompt(account: GoogleSignInAccount) {
        setLoading(true)
        lifecycleScope.launch {
            val backupMetadata = driveHelper?.getBackupMetadata()
            withContext(Dispatchers.Main) {
                if (backupMetadata != null) {
                    showRestorePromptDialog(backupMetadata.second)
                } else {
                    showEnableAutoBackupDialog()
                }
            }
        }
    }

    private fun showRestorePromptDialog(backupTimestamp: Long) {
        setLoading(false)
        val formattedDate = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(backupTimestamp))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.backup_found_title))
            .setMessage(getString(R.string.backup_found_message, formattedDate))
            .setPositiveButton(getString(R.string.action_restore_backup)) { _, _ ->
                performRestore()
            }
            .setNegativeButton(getString(R.string.action_start_fresh)) { _, _ ->
                navigateToMain(markAsSeen = true)
            }
            .setCancelable(false)
            .show()
    }

    private fun showEnableAutoBackupDialog() {
        setLoading(false)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.no_backup_found_title))
            .setMessage(getString(R.string.no_backup_found_message))
            .setPositiveButton(getString(R.string.action_enable_auto_backup)) { _, _ ->
                getSharedPreferences("AppPrefs", MODE_PRIVATE).edit {
                    putBoolean("auto_backup_enabled", true)
                }
                BackupScheduler.schedulePeriodicBackup(this)
                Toast.makeText(this, getString(R.string.auto_backup_enabled_toast), Toast.LENGTH_SHORT).show()
                navigateToMain(markAsSeen = true)
            }
            .setNegativeButton(getString(R.string.login_skip_button_text)) { _, _ ->
                navigateToMain(markAsSeen = true)
            }
            .setCancelable(false)
            .show()
    }

    private fun performRestore() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.restoring_dialog_title))
            .setMessage(getString(R.string.restoring_dialog_message))
            .setCancelable(false)
            .show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { driveHelper?.restoreData() }
            withContext(Dispatchers.Main) {
                dialog.dismiss()
                when (result) {
                    RestoreResult.SUCCESS -> {
                        driveHelper?.finalizeRestore(applicationContext)
                        CategoryManager.invalidate()
                        FileHashManager.invalidate()
                        Toast.makeText(this@LoginSuggestionActivity, getString(R.string.restore_success_restarting), Toast.LENGTH_LONG).show()
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
    }

    private fun showRestoreError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore_failed_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .setOnDismissListener {
                // Hata durumunda bile ana ekrana gitmeye devam et
                navigateToMain(markAsSeen = true)
            }
            .show()
    }

    private fun navigateToMain(markAsSeen: Boolean) {
        if (markAsSeen) {
            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            prefs.edit().putBoolean("has_seen_login_suggestion", true).apply()
        }
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val mainIntent = Intent.makeRestartActivityTask(intent!!.component)
        startActivity(mainIntent)
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}