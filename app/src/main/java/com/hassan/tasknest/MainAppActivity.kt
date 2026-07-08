package com.hassan.tasknest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.hassan.tasknest.data.repository.PreferencesRepository
import com.hassan.tasknest.databinding.ActivityMainAppBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

/** Hosts the primary NavHostFragment for all main-app destinations. */
class MainAppActivity : AppCompatActivity() {

    private val preferencesRepository: PreferencesRepository by inject()
    private lateinit var binding: ActivityMainAppBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = runBlocking { preferencesRepository.getThemeMode().first() }
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                "LIGHT" -> AppCompatDelegate.MODE_NIGHT_NO
                "DARK" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        super.onCreate(savedInstanceState)
        binding = ActivityMainAppBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
