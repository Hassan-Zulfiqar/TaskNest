package com.hassan.tasknest

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.hassan.tasknest.data.repository.PreferencesRepository
import com.hassan.tasknest.databinding.ActivityMainAppBinding
import com.hassan.tasknest.voice.VoskModelManager
import com.hassan.tasknest.voice.VoskModelState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

/** Hosts the primary NavHostFragment for all main-app destinations. */
class MainAppActivity : AppCompatActivity() {

    private val preferencesRepository: PreferencesRepository by inject()
    private val voskModelManager: VoskModelManager by inject()
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

        observeVoskModelState()
        setupBottomNavigation()
        setupExitConfirmationBackPress()
    }

    private fun observeVoskModelState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                voskModelManager.modelState.collect { state ->
                    when (state) {
                        is VoskModelState.Downloading -> {
                            binding.voskStatusBar.visibility = View.VISIBLE
                            binding.voskStatusText.text = "Preparing speech model... ${state.progressPercent}%"
                        }
                        VoskModelState.Unzipping -> {
                            binding.voskStatusBar.visibility = View.VISIBLE
                            binding.voskStatusText.text = "Preparing speech model..."
                        }
                        else -> {
                            binding.voskStatusBar.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun setupExitConfirmationBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as? NavHostFragment
                val navController = navHostFragment?.navController
                val currentDestinationId = navController?.currentDestination?.id

                if (currentDestinationId == R.id.taskListFragment || currentDestinationId == R.id.notesListFragment) {
                    AlertDialog.Builder(this@MainAppActivity)
                        .setTitle("Exit App")
                        .setMessage("Are you sure you want to exit?")
                        .setPositiveButton("Exit") { _, _ ->
                            finish()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return
                }

                if (navController?.navigateUp() == true) {
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    private fun setupBottomNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as? NavHostFragment
            ?: return
        val navController = navHostFragment.navController

        binding.bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_tasks -> {
                    navController.setGraph(R.navigation.tasks_nav_graph)
                    true
                }
                R.id.nav_notes -> {
                    navController.setGraph(R.navigation.notes_nav_graph)
                    true
                }
                else -> false
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigationView.visibility = if (
                destination.id == R.id.taskListFragment || destination.id == R.id.notesListFragment
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}
