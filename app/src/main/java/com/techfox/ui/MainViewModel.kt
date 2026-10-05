package com.techfox.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.techfox.data.PreferencesManager
import com.techfox.data.TranslationEntity
import com.techfox.data.TranslationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class UiState(
    val inputText: String = "",
    val targetLanguage: String = "Vietnamese",
    val isTranslating: Boolean = false,
    val currentTranslation: TranslationEntity? = null,
    val apiKey: String = "",
    val model: String = "gemini-3.1-flash-lite",
    val availableModels: List<String> = PRESET_MODELS,
    val isLoadingModels: Boolean = false,
    val customInstruction: String = "",
    val enableInsight: Boolean = true,
    val currentTab: Int = 0, // 0 = Translate, 1 = Subtitles, 2 = History, 3 = Settings
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val translationWarning: String? = null,
    val history: List<TranslationEntity> = emptyList(),
    val isOnboardingCompleted: Boolean = false
)

val PRESET_MODELS = listOf("gemini-3.1-flash-lite", "gemma-4-26b-a4b-it", "gemini-3.8-flash")

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TranslationRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeHistory()
        observePreferences()
    }

    fun reloadSettings() {
        loadSettings()
    }

    private fun loadSettings() {
        val savedKey = repository.preferences.getApiKey()
        val savedModel = repository.preferences.getModel()
        val savedLang = repository.preferences.getTargetLanguage()
        val savedInstruction = repository.preferences.getCustomInstruction()
        val savedInsightEnabled = repository.preferences.isInsightEnabled()
        val savedOnboarding = repository.preferences.isOnboardingCompleted()

        _uiState.value = _uiState.value.copy(
            apiKey = savedKey,
            model = savedModel.ifBlank { "gemini-3.1-flash-lite" },
            targetLanguage = savedLang.ifBlank { "Vietnamese" },
            customInstruction = savedInstruction,
            enableInsight = savedInsightEnabled,
            isOnboardingCompleted = savedOnboarding
        )

        if (savedKey.isNotBlank()) {
            fetchAvailableModels(savedKey)
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            PreferencesManager.preferenceChangedFlow.collect { key ->
                when (key) {
                    PreferencesManager.KEY_API_KEY -> {
                        val newKey = repository.preferences.getApiKey()
                        if (_uiState.value.apiKey != newKey) {
                            _uiState.value = _uiState.value.copy(apiKey = newKey)
                            if (newKey.isNotBlank()) {
                                fetchAvailableModels(newKey)
                            } else {
                                _uiState.value = _uiState.value.copy(availableModels = PRESET_MODELS, isLoadingModels = false)
                            }
                        }
                    }
                    PreferencesManager.KEY_TARGET_LANGUAGE -> {
                        val newLang = repository.preferences.getTargetLanguage()
                        if (_uiState.value.targetLanguage != newLang) {
                            _uiState.value = _uiState.value.copy(targetLanguage = newLang)
                        }
                    }
                    PreferencesManager.KEY_MODEL -> {
                        val newModel = repository.preferences.getModel()
                        if (_uiState.value.model != newModel) {
                            _uiState.value = _uiState.value.copy(model = newModel)
                        }
                    }
                    PreferencesManager.KEY_CUSTOM_INSTRUCTION -> {
                        val newInstr = repository.preferences.getCustomInstruction()
                        if (_uiState.value.customInstruction != newInstr) {
                            _uiState.value = _uiState.value.copy(customInstruction = newInstr)
                        }
                    }
                    PreferencesManager.KEY_ENABLE_INSIGHT -> {
                        val newInsight = repository.preferences.isInsightEnabled()
                        if (_uiState.value.enableInsight != newInsight) {
                            _uiState.value = _uiState.value.copy(enableInsight = newInsight)
                        }
                    }
                    PreferencesManager.KEY_ONBOARDING_COMPLETED -> {
                        val newOnboarding = repository.preferences.isOnboardingCompleted()
                        if (_uiState.value.isOnboardingCompleted != newOnboarding) {
                            _uiState.value = _uiState.value.copy(isOnboardingCompleted = newOnboarding)
                        }
                    }
                }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            repository.translationHistory.collectLatest { list ->
                _uiState.value = _uiState.value.copy(history = list)
            }
        }
    }

    fun fetchAvailableModels(apiKey: String = _uiState.value.apiKey) {
        val key = apiKey.trim()
        if (key.isBlank()) {
            _uiState.value = _uiState.value.copy(availableModels = PRESET_MODELS, isLoadingModels = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingModels = true)
            val result = repository.fetchAvailableModels(key)
            result.onSuccess { models ->
                _uiState.value = _uiState.value.copy(
                    availableModels = models.ifEmpty { PRESET_MODELS },
                    isLoadingModels = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    availableModels = PRESET_MODELS,
                    isLoadingModels = false
                )
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text, errorMessage = null)
    }

    fun onTargetLanguageChanged(language: String) {
        _uiState.value = _uiState.value.copy(targetLanguage = language)
        repository.preferences.setTargetLanguage(language)
    }

    fun onApiKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key)
        repository.preferences.setApiKey(key)
        if (key.isNotBlank()) {
            fetchAvailableModels(key)
        } else {
            _uiState.value = _uiState.value.copy(availableModels = PRESET_MODELS, isLoadingModels = false)
        }
    }

    fun onModelChanged(model: String) {
        _uiState.value = _uiState.value.copy(model = model)
        repository.preferences.setModel(model)
    }

    fun onCustomInstructionChanged(instruction: String) {
        _uiState.value = _uiState.value.copy(customInstruction = instruction)
        repository.preferences.setCustomInstruction(instruction)
    }

    fun onEnableInsightChanged(enable: Boolean) {
        _uiState.value = _uiState.value.copy(enableInsight = enable)
        repository.preferences.setInsightEnabled(enable)
    }

    fun switchTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(currentTab = tabIndex)
    }

    fun clearInput() {
        _uiState.value = _uiState.value.copy(
            inputText = "",
            currentTranslation = null,
            errorMessage = null,
            translationWarning = null
        )
    }

    fun translate(isStrictRetry: Boolean = false) {
        val currentText = _uiState.value.inputText.trim()
        if (currentText.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter text to translate")
            return
        }

        val lang = _uiState.value.targetLanguage
        val previousWarning = if (isStrictRetry) _uiState.value.translationWarning.orEmpty() else ""
        val existingId = if (isStrictRetry) _uiState.value.currentTranslation?.id else null

        _uiState.value = _uiState.value.copy(
            isTranslating = true,
            errorMessage = null,
            translationWarning = null
        )

        viewModelScope.launch {
            val result = repository.translateAndSave(
                text = currentText,
                targetLanguage = lang,
                isStrictRetry = isStrictRetry,
                previousWarning = previousWarning,
                existingEntityId = existingId
            )
            result.onSuccess { (entity, warning) ->
                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    currentTranslation = entity,
                    translationWarning = warning
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    errorMessage = error.localizedMessage ?: "Translation failed"
                )
            }
        }
    }

    fun retryTranslationStrict() {
        translate(isStrictRetry = true)
    }

    fun dismissWarning() {
        _uiState.value = _uiState.value.copy(translationWarning = null)
    }

    fun dismissMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null,
            translationWarning = null
        )
    }

    fun selectHistoryItem(item: TranslationEntity) {
        _uiState.value = _uiState.value.copy(
            inputText = item.sourceText,
            targetLanguage = item.targetLanguage,
            currentTranslation = item,
            currentTab = 0
        )
    }

    fun deleteHistoryItem(item: TranslationEntity) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

}
