package com.hassan.tasknest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hassan.tasknest.databinding.ActivityMainAppBinding

/** Hosts the primary NavHostFragment for all main-app destinations. */
class MainAppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainAppBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainAppBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
