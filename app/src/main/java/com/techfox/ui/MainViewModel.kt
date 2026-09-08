package com.techfox.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.techfox.data.TranslationEntity
import com.techfox.data.TranslationRepository
import com.techfox.ui.components.FULL_LANGUAGE_LIST
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
    val history: List<TranslationEntity> = emptyList(),
    val isOnboardingCompleted: Boolean = false
)

val PRESET_LANGUAGES: List<String> = FULL_LANGUAGE_LIST
val PRESET_MODELS = listOf("gemini-3.1-flash-lite", "gemma-4-26b-a4b-it", "gemini-3.8-flash")

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TranslationRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeHistory()
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
            errorMessage = null
        )
    }

    fun translate() {
        val currentText = _uiState.value.inputText.trim()
        if (currentText.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter text to translate")
            return
        }

        val lang = _uiState.value.targetLanguage
        _uiState.value = _uiState.value.copy(isTranslating = true, errorMessage = null)

        viewModelScope.launch {
            val result = repository.translateAndSave(currentText, lang)
            result.onSuccess { entity ->
                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    currentTranslation = entity
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    errorMessage = error.localizedMessage ?: "Translation failed"
                )
            }
        }
    }

    fun dismissMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
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
