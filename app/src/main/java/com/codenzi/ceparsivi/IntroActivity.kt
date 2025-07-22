package com.codenzi.ceparsivi

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.viewpager2.widget.ViewPager2
import com.codenzi.ceparsivi.databinding.ActivityIntroBinding

class IntroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntroBinding
    private val introSliderAdapter by lazy {
        IntroSliderAdapter(
            listOf(
                IntroSlide(
                    getString(R.string.intro_welcome_title),
                    getString(R.string.intro_welcome_description),
                    R.drawable.ic_intro_welcome
                ),
                IntroSlide(
                    getString(R.string.intro_archive_title),
                    getString(R.string.intro_archive_description),
                    R.drawable.ic_intro_add_file
                ),
                IntroSlide(
                    getString(R.string.intro_backup_title),
                    getString(R.string.intro_backup_description),
                    R.drawable.ic_intro_backup
                )
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.introSliderViewPager.adapter = introSliderAdapter
        setupIndicators()
        setCurrentIndicator(0)

        binding.introSliderViewPager.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                setCurrentIndicator(position)
            }
        })

        binding.buttonNext.setOnClickListener {
            if (binding.introSliderViewPager.currentItem + 1 < introSliderAdapter.itemCount) {
                binding.introSliderViewPager.currentItem += 1
            } else {
                navigateToNextScreen()
            }
        }

        binding.textSkipIntro.setOnClickListener {
            navigateToNextScreen()
        }
    }

    private fun navigateToNextScreen() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.edit().putBoolean("isFirstLaunch", false).apply()
        // Bu aktivitenin görevi bitti, şimdi Google Giriş Öneri ekranına yönlendiriyoruz.
        Intent(applicationContext, LoginSuggestionActivity::class.java).also {
            startActivity(it)
            finish()
        }
    }

    private fun setupIndicators() {
        val indicators = arrayOfNulls<ImageView>(introSliderAdapter.itemCount)
        val layoutParams: LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        layoutParams.setMargins(8, 0, 8, 0)
        for (i in indicators.indices) {
            indicators[i] = ImageView(applicationContext)
            indicators[i].apply {
                this?.setImageDrawable(
                    ContextCompat.getDrawable(
                        applicationContext,
                        R.drawable.indicator_inactive
                    )
                )
                this?.layoutParams = layoutParams
            }
            binding.indicatorsContainer.addView(indicators[i])
        }
    }

    private fun setCurrentIndicator(index: Int) {
        val childCount = binding.indicatorsContainer.childCount
        for (i in 0 until childCount) {
            val imageView = binding.indicatorsContainer[i] as ImageView
            if (i == index) {
                imageView.setImageDrawable(
                    ContextCompat.getDrawable(
                        applicationContext,
                        R.drawable.indicator_active
                    )
                )
            } else {
                imageView.setImageDrawable(
                    ContextCompat.getDrawable(
                        applicationContext,
                        R.drawable.indicator_inactive
                    )
                )
            }
        }
        if (index == introSliderAdapter.itemCount - 1) {
            binding.buttonNext.text = getString(R.string.action_finish)
        } else {
            binding.buttonNext.text = getString(R.string.action_next)
        }
    }
}