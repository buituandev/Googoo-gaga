package com.example.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.MainActivity
import com.example.data.PreferencesManager
import com.example.ui.theme.MyApplicationTheme

class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isFromSettings = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)
        val preferences = PreferencesManager(this)

        setContent {
            MyApplicationTheme {
                OnboardingScreen(
                    onComplete = { apiKey ->
                        val trimmed = apiKey.trim()
                        if (trimmed.isNotBlank()) {
                            preferences.setApiKey(trimmed)
                        }
                        preferences.setOnboardingCompleted(true)

                        if (isFromSettings) {
                            finish()
                        } else {
                            val intent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                    },
                    onClose = if (isFromSettings) {
                        { finish() }
                    } else null
                )
            }
        }
    }

    companion object {
        const val EXTRA_FROM_SETTINGS = "extra_from_settings"

        fun createIntent(context: Context, fromSettings: Boolean = false): Intent {
            return Intent(context, OnboardingActivity::class.java).apply {
                putExtra(EXTRA_FROM_SETTINGS, fromSettings)
            }
        }
    }
}

