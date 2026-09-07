package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_translator_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
        private const val KEY_TARGET_LANGUAGE = "target_language"
        private const val KEY_CUSTOM_INSTRUCTION = "custom_instruction"
        private const val KEY_ENABLE_INSIGHT = "enable_insight"

        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
        const val DEFAULT_TARGET_LANG = "Vietnamese"
    }

    fun isInsightEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLE_INSIGHT, true)
    }

    fun setInsightEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLE_INSIGHT, enabled) }
    }

    fun getCustomInstruction(): String {
        return prefs.getString(KEY_CUSTOM_INSTRUCTION, "") ?: ""
    }

    fun setCustomInstruction(instruction: String) {
        prefs.edit { putString(KEY_CUSTOM_INSTRUCTION, instruction.trim()) }
    }

    fun getApiKey(): String {
        val userSavedKey = prefs.getString(KEY_API_KEY, "") ?: ""
        if (userSavedKey.isNotBlank()) {
            return userSavedKey
        }
        // Fallback to BuildConfig key if injected via secrets
        return try {
            ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun setApiKey(apiKey: String) {
        prefs.edit { putString(KEY_API_KEY, apiKey.trim()) }
    }

    fun getModel(): String {
        return prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setModel(model: String) {
        prefs.edit { putString(KEY_MODEL, model) }
    }

    fun getTargetLanguage(): String {
        return prefs.getString(KEY_TARGET_LANGUAGE, DEFAULT_TARGET_LANG) ?: DEFAULT_TARGET_LANG
    }

    fun setTargetLanguage(language: String) {
        prefs.edit { putString(KEY_TARGET_LANGUAGE, language) }
    }
}
