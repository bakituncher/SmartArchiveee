// Konum: app/src/main/java/com/codenzi/ceparsivi/SplashActivity.kt

package com.codenzi.ceparsivi

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

class SplashActivity : AppCompatActivity() {

    private lateinit var consentInformation: ConsentInformation
    private val isMobileAdsInitialized = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Bu aktivitenin bir arayüzü (UI) olmadığı için setContentView çağrısı yok.

        // Rıza formu için standart parametreler.
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        // Rıza bilgilerini güncelliyoruz.
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                // Rıza bilgileri başarıyla güncellendi.
                // Google'ın kütüphanesi, formun gösterilmesi gerekip gerekmediğini kontrol edecek.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { loadAndShowError ->
                    if (loadAndShowError != null) {
                        Log.w("SplashActivity", "Rıza formu hatası: ${loadAndShowError.message}")
                    }
                    // Form gösterilse de gösterilmese de, işlem bittikten sonra ana uygulamayı başlat.
                    startMainApplication()
                }
            },
            { requestConsentError ->
                // Rıza bilgileri alınamadı.
                Log.w("SplashActivity", "Rıza bilgisi alınamadı: ${requestConsentError.message}")
                // Hata durumunda bile uygulamayı başlatmaya devam et.
                startMainApplication()
            }
        )
    }

    private fun startMainApplication() {
        // Bu fonksiyonun sadece bir kez çalışmasını garantiliyoruz.
        if (isMobileAdsInitialized.getAndSet(true)) {
            return
        }

        // Uygulama sınıfındaki reklamları başlatma fonksiyonunu çağırıyoruz.
        (application as CepArsiviApplication).initializeMobileAds()

        // Orijinal yönlendirme mantığınız burada devam ediyor.
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)
        val hasSeenLoginSuggestion = prefs.getBoolean("has_seen_login_suggestion", false)

        val intent = when {
            isFirstLaunch -> Intent(this, IntroActivity::class.java)
            !hasSeenLoginSuggestion -> Intent(this, LoginSuggestionActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish() // Bu aktivitenin işi bitti, kapatıyoruz.
    }
}