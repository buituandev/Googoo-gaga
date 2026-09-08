package com.techfox.ui.subtitle

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.techfox.R
import com.techfox.data.subtitle.InputFormat
import com.techfox.data.subtitle.SubtitleCharacter
import com.techfox.data.subtitle.SubtitleCue
import com.techfox.data.subtitle.SubtitleJobEntity
import com.techfox.data.subtitle.SubtitleParser
import com.techfox.data.subtitle.SubtitlePreset
import com.techfox.data.subtitle.SubtitleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubtitleUiState(
    val inputText: String = "",
    val fileName: String? = null,
    val detectedFormat: InputFormat = InputFormat.PLAIN_TEXT,
    val targetLanguage: String = "Vietnamese",
    val contextDescription: String = "",
    val characters: List<SubtitleCharacter> = emptyList(),

    // Subtitle Tone Preset & Custom Directives
    val selectedPreset: SubtitlePreset = SubtitlePreset.DEFAULT_PRESET,
    val customPrompt: String = "",

    // Translation Progress Tracking
    val isTranslating: Boolean = false,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val completedCues: Int = 0,
    val totalCues: Int = 0,
    val noiseCuesPreserved: Int = 0,

    // Result & Completion
    val translatedOutputText: String = "",
    val translatedCues: List<SubtitleCue> = emptyList(),
    val isCompleted: Boolean = false,

    // Smart Pickup Checkpoint
    val resumableJob: SubtitleJobEntity? = null,

    // Error & Feedback
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class SubtitleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SubtitleRepository(application)

    private val _uiState = MutableStateFlow(SubtitleUiState())
    val uiState: StateFlow<SubtitleUiState> = _uiState.asStateFlow()

    private var translationJob: Job? = null

    private fun getString(@StringRes resId: Int): String =
        getApplication<Application>().getString(resId)

    private fun getString(@StringRes resId: Int, vararg formatArgs: Any): String =
        getApplication<Application>().getString(resId, *formatArgs)

    init {
        val savedLang = repository.preferences.getTargetLanguage()
        val savedPresetId = repository.preferences.getSubtitlePresetId()
        val savedCustomPrompt = repository.preferences.getCustomSubtitlePrompt()
        _uiState.value = _uiState.value.copy(
            targetLanguage = if (savedLang.isNotBlank()) savedLang else "Vietnamese",
            selectedPreset = SubtitlePreset.getById(savedPresetId),
            customPrompt = savedCustomPrompt
        )
        checkLatestResumableJob()
    }

    private fun checkLatestResumableJob() {
        viewModelScope.launch {
            val job = repository.getLatestActiveJob()
            if (job != null && job.status == "PAUSED") {
                _uiState.value = _uiState.value.copy(resumableJob = job)
            }
        }
    }

    fun onInputTextChanged(text: String) {
        val format = SubtitleParser.detectFormat(text, _uiState.value.fileName)
        _uiState.value = _uiState.value.copy(
            inputText = text,
            detectedFormat = format,
            errorMessage = null
        )
        checkResumableJobForCurrentInput()
    }

    fun onFileLoaded(fileName: String, content: String) {
        val format = SubtitleParser.detectFormat(content, fileName)
        _uiState.value = _uiState.value.copy(
            fileName = fileName,
            inputText = content,
            detectedFormat = format,
            errorMessage = null
        )
        checkResumableJobForCurrentInput()
    }

    private fun checkResumableJobForCurrentInput() {
        val state = _uiState.value
        if (state.inputText.isBlank()) return

        val jobId = repository.computeJobId(
            content = state.inputText,
            fileName = state.fileName ?: "pasted_text",
            targetLang = state.targetLanguage
        )

        viewModelScope.launch {
            val job = repository.getJobById(jobId)
            if (job != null && job.status == "PAUSED") {
                _uiState.value = _uiState.value.copy(resumableJob = job)
            }
        }
    }

    fun onTargetLanguageChanged(language: String) {
        _uiState.value = _uiState.value.copy(targetLanguage = language)
        repository.preferences.setTargetLanguage(language)
        checkResumableJobForCurrentInput()
    }

    fun onContextDescriptionChanged(contextDesc: String) {
        _uiState.value = _uiState.value.copy(contextDescription = contextDesc)
    }

    fun onPresetSelected(preset: SubtitlePreset) {
        _uiState.value = _uiState.value.copy(selectedPreset = preset)
        repository.preferences.setSubtitlePresetId(preset.id)
    }

    fun onCustomPromptChanged(prompt: String) {
        _uiState.value = _uiState.value.copy(customPrompt = prompt)
        repository.preferences.setCustomSubtitlePrompt(prompt)
    }

    fun addCharacter() {
        if (_uiState.value.characters.size >= SubtitleCharacter.MAX_CHARACTERS_COUNT) return
        val current = _uiState.value.characters.toMutableList()
        current.add(SubtitleCharacter())
        _uiState.value = _uiState.value.copy(characters = current)
    }

    fun updateCharacter(id: String, name: String, description: String) {
        val updated = _uiState.value.characters.map { char ->
            if (char.id == id) {
                char.copy(name = name, description = description)
            } else {
                char
            }
        }
        _uiState.value = _uiState.value.copy(characters = updated)
    }

    fun removeCharacter(id: String) {
        val updated = _uiState.value.characters.filter { it.id != id }
        _uiState.value = _uiState.value.copy(characters = updated)
    }

    fun startTranslation() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = getString(R.string.error_empty_subtitle_input))
            return
        }

        val apiKey = repository.preferences.getApiKey()
        if (apiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = getString(R.string.error_missing_api_key))
            return
        }

        val jobId = repository.computeJobId(
            content = text,
            fileName = state.fileName ?: "pasted_text",
            targetLang = state.targetLanguage
        )

        val activePreset = state.selectedPreset
        val customInstruction = if (activePreset.isCustom) {
            state.customPrompt
        } else {
            repository.preferences.getCustomInstruction()
        }

        if (state.detectedFormat.isSubtitle) {
            val parseResult = SubtitleParser.parseSubtitle(text, state.detectedFormat)
            if (parseResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = parseResult.exceptionOrNull()?.localizedMessage ?: getString(R.string.error_invalid_subtitle_file)
                )
                return
            }

            val cues = parseResult.getOrThrow()
            val noiseCount = cues.count { it.isNoise }
            val translatableCount = cues.size - noiseCount
            val estimatedChunks = ((translatableCount + 34) / 35).coerceAtLeast(1)

            _uiState.value = _uiState.value.copy(
                isTranslating = true,
                isCompleted = false,
                currentChunk = 0,
                totalChunks = estimatedChunks,
                completedCues = 0,
                totalCues = cues.size,
                noiseCuesPreserved = noiseCount,
                errorMessage = null,
                resumableJob = null
            )

            translationJob = viewModelScope.launch {
                val result = repository.translateSubtitleWithCheckpoints(
                    jobId = jobId,
                    fileName = state.fileName ?: "subtitle.${state.detectedFormat.extension}",
                    format = state.detectedFormat,
                    cues = cues,
                    targetLanguage = state.targetLanguage,
                    contextDescription = state.contextDescription,
                    characters = state.characters,
                    preset = activePreset,
                    customInstruction = customInstruction,
                    onProgress = { chunkIndex, totalChunks, completed, total, noise ->
                        _uiState.value = _uiState.value.copy(
                            currentChunk = chunkIndex,
                            totalChunks = totalChunks,
                            completedCues = completed,
                            totalCues = total,
                            noiseCuesPreserved = noise
                        )
                    }
                )

                result.onSuccess { translatedCues ->
                    val outputText = if (state.detectedFormat == InputFormat.SUBTITLE_VTT) {
                        SubtitleParser.assembleVtt(translatedCues)
                    } else {
                        SubtitleParser.assembleSrt(translatedCues)
                    }

                    _uiState.value = _uiState.value.copy(
                        isTranslating = false,
                        isCompleted = true,
                        translatedCues = translatedCues,
                        translatedOutputText = outputText,
                        successMessage = getString(R.string.success_subtitle_translated),
                        resumableJob = null
                    )
                }.onFailure { error ->
                    val pausedJob = repository.getJobById(jobId)
                    _uiState.value = _uiState.value.copy(
                        isTranslating = false,
                        errorMessage = error.localizedMessage ?: getString(R.string.error_translation_interrupted),
                        resumableJob = pausedJob
                    )
                }
            }
        } else {
            // Formatted text (JSON, MD, LRC, Plain text)
            _uiState.value = _uiState.value.copy(
                isTranslating = true,
                isCompleted = false,
                errorMessage = null
            )

            translationJob = viewModelScope.launch {
                val result = repository.translateFormattedText(
                    text = text,
                    targetLanguage = state.targetLanguage,
                    contextDescription = state.contextDescription,
                    preset = activePreset,
                    customInstruction = customInstruction
                )

                result.onSuccess { formattedResult ->
                    _uiState.value = _uiState.value.copy(
                        isTranslating = false,
                        isCompleted = true,
                        translatedOutputText = formattedResult,
                        successMessage = getString(R.string.success_formatted_translated)
                    )
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isTranslating = false,
                        errorMessage = error.localizedMessage ?: getString(R.string.error_formatted_translation_failed)
                    )
                }
            }
        }
    }

    fun resumeTranslation() {
        val job = _uiState.value.resumableJob ?: return
        val cues = SubtitleJobEntity.deserializeCues(job.cuesJson)
        val format = try { InputFormat.valueOf(job.format) } catch (e: Exception) { InputFormat.SUBTITLE_SRT }

        val activePreset = _uiState.value.selectedPreset
        val customInstruction = if (activePreset.isCustom) {
            _uiState.value.customPrompt
        } else {
            repository.preferences.getCustomInstruction()
        }

        _uiState.value = _uiState.value.copy(
            isTranslating = true,
            isCompleted = false,
            currentChunk = job.completedChunks,
            totalChunks = job.totalChunks,
            completedCues = job.completedCues,
            totalCues = job.totalCues,
            noiseCuesPreserved = job.noiseCuesCount,
            errorMessage = null,
            resumableJob = null
        )

        translationJob = viewModelScope.launch {
            val result = repository.translateSubtitleWithCheckpoints(
                jobId = job.jobId,
                fileName = job.fileName,
                format = format,
                cues = cues,
                targetLanguage = job.targetLanguage,
                contextDescription = job.contextDescription,
                characters = SubtitleJobEntity.deserializeCharacters(job.charactersJson),
                preset = activePreset,
                customInstruction = customInstruction,
                onProgress = { chunkIndex, totalChunks, completed, total, noise ->
                    _uiState.value = _uiState.value.copy(
                        currentChunk = chunkIndex,
                        totalChunks = totalChunks,
                        completedCues = completed,
                        totalCues = total,
                        noiseCuesPreserved = noise
                    )
                }
            )

            result.onSuccess { translatedCues ->
                val outputText = if (format == InputFormat.SUBTITLE_VTT) {
                    SubtitleParser.assembleVtt(translatedCues)
                } else {
                    SubtitleParser.assembleSrt(translatedCues)
                }

                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    isCompleted = true,
                    translatedCues = translatedCues,
                    translatedOutputText = outputText,
                    successMessage = getString(R.string.success_subtitle_translated),
                    resumableJob = null
                )
            }.onFailure { error ->
                val pausedJob = repository.getJobById(job.jobId)
                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    errorMessage = error.localizedMessage ?: getString(R.string.error_translation_interrupted),
                    resumableJob = pausedJob
                )
            }
        }
    }

    fun discardResumableJob() {
        val job = _uiState.value.resumableJob ?: return
        viewModelScope.launch {
            repository.deleteJob(job.jobId)
            _uiState.value = _uiState.value.copy(resumableJob = null)
        }
    }

    fun cancelTranslation() {
        translationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isTranslating = false,
            errorMessage = getString(R.string.msg_translation_stopped)
        )
        checkLatestResumableJob()
    }

    fun clearInput() {
        _uiState.value = _uiState.value.copy(
            inputText = "",
            fileName = null,
            detectedFormat = InputFormat.PLAIN_TEXT,
            translatedOutputText = "",
            translatedCues = emptyList(),
            isCompleted = false,
            characters = emptyList(),
            errorMessage = null,
            successMessage = null,
            resumableJob = null
        )
    }

    fun dismissMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
