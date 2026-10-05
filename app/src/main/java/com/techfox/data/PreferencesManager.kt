package com.techfox.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_translator_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_API_KEY = "gemini_api_key"
        const val KEY_MODEL = "gemini_model"
        const val KEY_TARGET_LANGUAGE = "target_language"
        const val KEY_CUSTOM_INSTRUCTION = "custom_instruction"
        const val KEY_ENABLE_INSIGHT = "enable_insight"
        const val KEY_ENABLE_TOKEN_GUARD = "enable_token_guard"
        const val KEY_SUBTITLE_PRESET_ID = "subtitle_preset_id"
        const val KEY_CUSTOM_SUBTITLE_PROMPT = "custom_subtitle_prompt"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
        const val DEFAULT_TARGET_LANG = "Vietnamese"
        const val DEFAULT_SUBTITLE_PRESET_ID = "cinematic"

        private val _preferenceChangedFlow = MutableSharedFlow<String>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val preferenceChangedFlow: SharedFlow<String> = _preferenceChangedFlow.asSharedFlow()

        fun notifyPreferenceChanged(key: String) {
            _preferenceChangedFlow.tryEmit(key)
        }
    }

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null) {
            _preferenceChangedFlow.tryEmit(key)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, completed) }
        notifyPreferenceChanged(KEY_ONBOARDING_COMPLETED)
    }

    fun isInsightEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLE_INSIGHT, true)
    }

    fun setInsightEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLE_INSIGHT, enabled) }
        notifyPreferenceChanged(KEY_ENABLE_INSIGHT)
    }

    fun isTokenGuardEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLE_TOKEN_GUARD, true)
    }

    fun setTokenGuardEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLE_TOKEN_GUARD, enabled) }
        notifyPreferenceChanged(KEY_ENABLE_TOKEN_GUARD)
    }

    fun getCustomInstruction(): String {
        return prefs.getString(KEY_CUSTOM_INSTRUCTION, "") ?: ""
    }

    fun setCustomInstruction(instruction: String) {
        prefs.edit { putString(KEY_CUSTOM_INSTRUCTION, instruction.trim()) }
        notifyPreferenceChanged(KEY_CUSTOM_INSTRUCTION)
    }

    fun getSubtitlePresetId(): String {
        return prefs.getString(KEY_SUBTITLE_PRESET_ID, DEFAULT_SUBTITLE_PRESET_ID) ?: DEFAULT_SUBTITLE_PRESET_ID
    }

    fun setSubtitlePresetId(presetId: String) {
        prefs.edit { putString(KEY_SUBTITLE_PRESET_ID, presetId.trim()) }
        notifyPreferenceChanged(KEY_SUBTITLE_PRESET_ID)
    }

    fun getCustomSubtitlePrompt(): String {
        return prefs.getString(KEY_CUSTOM_SUBTITLE_PROMPT, "") ?: ""
    }

    fun setCustomSubtitlePrompt(prompt: String) {
        prefs.edit { putString(KEY_CUSTOM_SUBTITLE_PROMPT, prompt) }
        notifyPreferenceChanged(KEY_CUSTOM_SUBTITLE_PROMPT)
    }

    fun getApiKey(): String {
        val userSavedKey = prefs.getString(KEY_API_KEY, "") ?: ""
        if (userSavedKey.isNotBlank()) {
            return userSavedKey
        }
        return try {
            ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun setApiKey(apiKey: String) {
        prefs.edit { putString(KEY_API_KEY, apiKey.trim()) }
        notifyPreferenceChanged(KEY_API_KEY)
    }

    fun getModel(): String {
        return prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setModel(model: String) {
        prefs.edit { putString(KEY_MODEL, model) }
        notifyPreferenceChanged(KEY_MODEL)
    }

    fun getTargetLanguage(): String {
        return prefs.getString(KEY_TARGET_LANGUAGE, DEFAULT_TARGET_LANG) ?: DEFAULT_TARGET_LANG
    }

    fun setTargetLanguage(language: String) {
        prefs.edit { putString(KEY_TARGET_LANGUAGE, language) }
        notifyPreferenceChanged(KEY_TARGET_LANGUAGE)
    }
}
