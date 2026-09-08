package com.techfox.data

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
        private const val KEY_SUBTITLE_PRESET_ID = "subtitle_preset_id"
        private const val KEY_CUSTOM_SUBTITLE_PROMPT = "custom_subtitle_prompt"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
        const val DEFAULT_TARGET_LANG = "Vietnamese"
        const val DEFAULT_SUBTITLE_PRESET_ID = "cinematic"
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, completed) }
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

    fun getSubtitlePresetId(): String {
        return prefs.getString(KEY_SUBTITLE_PRESET_ID, DEFAULT_SUBTITLE_PRESET_ID) ?: DEFAULT_SUBTITLE_PRESET_ID
    }

    fun setSubtitlePresetId(presetId: String) {
        prefs.edit { putString(KEY_SUBTITLE_PRESET_ID, presetId.trim()) }
    }

    fun getCustomSubtitlePrompt(): String {
        return prefs.getString(KEY_CUSTOM_SUBTITLE_PROMPT, "") ?: ""
    }

    fun setCustomSubtitlePrompt(prompt: String) {
        prefs.edit { putString(KEY_CUSTOM_SUBTITLE_PROMPT, prompt) }
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
