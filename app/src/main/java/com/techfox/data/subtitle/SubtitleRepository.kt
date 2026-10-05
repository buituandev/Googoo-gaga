package com.techfox.data.subtitle

import android.content.Context
import com.techfox.data.AppDatabase
import com.techfox.data.PreferencesManager
import java.security.MessageDigest

class SubtitleRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val jobDao = db.subtitleJobDao()
    private val translatorService = SubtitleTranslatorService()
    val preferences = PreferencesManager(context)

    suspend fun getLatestActiveJob(): SubtitleJobEntity? = jobDao.getLatestActiveJob()

    suspend fun getJobById(jobId: String): SubtitleJobEntity? = jobDao.getJobById(jobId)

    suspend fun deleteJob(jobId: String) = jobDao.deleteJobById(jobId)

    /**
     * Compute a deterministic unique Job ID based on the file content/name, target language,
     * and token guard mode, preventing collision while enabling instant pickup.
     */
    fun computeJobId(content: String, fileName: String, targetLang: String, enableTokenGuard: Boolean = true): String {
        val raw = "$fileName:$targetLang:$enableTokenGuard:${content.take(500)}:${content.length}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    suspend fun pauseJob(jobId: String, errorMessage: String? = null) {
        val existing = jobDao.getJobById(jobId) ?: return
        if (existing.status != "COMPLETED") {
            val paused = existing.copy(
                status = "PAUSED",
                errorMessage = errorMessage ?: existing.errorMessage,
                updatedAt = System.currentTimeMillis()
            )
            jobDao.saveJob(paused)
        }
    }

    /**
     * Translates a subtitle with chunking and auto-save checkpoints for smart pickup.
     */
    suspend fun translateSubtitleWithCheckpoints(
        jobId: String,
        fileName: String,
        format: InputFormat,
        cues: List<SubtitleCue>,
        targetLanguage: String,
        contextDescription: String,
        characters: List<SubtitleCharacter>,
        preset: SubtitlePreset = SubtitlePreset.DEFAULT_PRESET,
        customInstruction: String = preferences.getCustomInstruction(),
        enableTokenGuard: Boolean = preferences.isTokenGuardEnabled(),
        onProgress: suspend (currentChunk: Int, totalChunks: Int, completedCues: Int, totalCues: Int, noiseCues: Int) -> Unit
    ): Result<List<SubtitleCue>> {
        val apiKey = preferences.getApiKey()
        val model = preferences.getModel()

        // Check if there is an existing job to resume from
        val existingJob = jobDao.getJobById(jobId)
        val hasCheckpoint = existingJob != null &&
            existingJob.status != "COMPLETED" &&
            existingJob.cuesJson.isNotBlank() &&
            (existingJob.completedChunks > 0 || existingJob.completedCues > 0)

        val workingCues = if (hasCheckpoint) {
            val savedCues = SubtitleJobEntity.deserializeCues(existingJob.cuesJson)
            if (savedCues.size == cues.size) savedCues else cues
        } else {
            cues
        }

        val noiseCount = if (enableTokenGuard) workingCues.count { it.isNoise } else 0
        val allTranslatableIndices = workingCues.indices.filter {
            if (enableTokenGuard) !workingCues[it].isNoise else true
        }
        val smartChunks = SubtitleTranslatorService.createSmartChunks(allTranslatableIndices, workingCues)
        val estimatedTotalChunks = smartChunks.size.coerceAtLeast(1)

        val startingChunk = if (hasCheckpoint) {
            val firstIncomplete = smartChunks.indexOfFirst { chunkIndices ->
                chunkIndices.any { workingCues[it].translatedText == null }
            }
            if (firstIncomplete != -1) firstIncomplete else 0
        } else {
            0
        }

        val originalRawText = existingJob?.originalContent?.takeIf { it.isNotBlank() } ?: (
            if (format == InputFormat.SUBTITLE_VTT) SubtitleParser.assembleVtt(cues)
            else SubtitleParser.assembleSrt(cues)
        )

        // Save initial job state as IN_PROGRESS
        val initialJob = SubtitleJobEntity(
            jobId = jobId,
            fileName = fileName,
            format = format.name,
            targetLanguage = targetLanguage,
            contextDescription = contextDescription,
            charactersJson = SubtitleJobEntity.serializeCharacters(characters),
            originalContent = originalRawText,
            cuesJson = SubtitleJobEntity.serializeCues(workingCues),
            totalChunks = estimatedTotalChunks,
            completedChunks = startingChunk,
            totalCues = workingCues.size,
            completedCues = workingCues.count { it.translatedText != null },
            noiseCuesCount = noiseCount,
            status = "IN_PROGRESS"
        )
        jobDao.saveJob(initialJob)

        val translationResult = translatorService.translateSubtitleInChunks(
            cues = workingCues,
            targetLanguage = targetLanguage,
            modelName = model,
            apiKey = apiKey,
            contextDescription = contextDescription,
            characters = characters,
            customInstruction = customInstruction,
            preset = preset,
            chunkSize = 35,
            startingChunkIndex = startingChunk,
            enableTokenGuard = enableTokenGuard,
            onChunkProgress = { chunkIndex, totalChunks, currentCues ->
                val completedCuesCount = currentCues.count { it.translatedText != null }
                // Checkpoint auto-save into Room DB
                val updatedJob = initialJob.copy(
                    completedChunks = chunkIndex,
                    totalChunks = totalChunks,
                    completedCues = completedCuesCount,
                    cuesJson = SubtitleJobEntity.serializeCues(currentCues),
                    status = if (chunkIndex >= totalChunks) "COMPLETED" else "IN_PROGRESS",
                    updatedAt = System.currentTimeMillis()
                )
                jobDao.saveJob(updatedJob)
                onProgress(chunkIndex, totalChunks, completedCuesCount, currentCues.size, noiseCount)
            }
        )

        return if (translationResult.isSuccess) {
            val finalCues = translationResult.getOrThrow()

            // Double check: Verify timestamps and cue integrity
            val validation = SubtitleParser.validateDoubleCheck(cues, finalCues)
            if (validation.isFailure) {
                val error = validation.exceptionOrNull() ?: Exception("Subtitle validation failed")
                val latestState = jobDao.getJobById(jobId) ?: initialJob
                val pausedJob = latestState.copy(
                    status = "FAILED",
                    errorMessage = error.localizedMessage
                )
                jobDao.saveJob(pausedJob)
                return Result.failure(error)
            }

            // Save completed state
            val completedText = if (format == InputFormat.SUBTITLE_VTT) {
                SubtitleParser.assembleVtt(finalCues)
            } else {
                SubtitleParser.assembleSrt(finalCues)
            }

            val finalJob = initialJob.copy(
                completedChunks = estimatedTotalChunks,
                status = "COMPLETED",
                resultText = completedText,
                cuesJson = SubtitleJobEntity.serializeCues(finalCues),
                updatedAt = System.currentTimeMillis()
            )
            jobDao.saveJob(finalJob)

            Result.success(finalCues)
        } else {
            val error = translationResult.exceptionOrNull() ?: Exception("Translation failed")
            // Preserve the latest saved progress in Room DB instead of resetting to initialJob
            val latestState = jobDao.getJobById(jobId) ?: initialJob
            val pausedJob = latestState.copy(
                status = "PAUSED",
                errorMessage = error.localizedMessage,
                updatedAt = System.currentTimeMillis()
            )
            jobDao.saveJob(pausedJob)
            Result.failure(error)
        }
    }

    /**
     * Translates formatted text (JSON, MD, LRC, Plain Text) ensuring the original format is kept.
     */
    suspend fun translateFormattedText(
        text: String,
        targetLanguage: String,
        contextDescription: String,
        preset: SubtitlePreset = SubtitlePreset.DEFAULT_PRESET,
        customInstruction: String = preferences.getCustomInstruction()
    ): Result<String> {
        val apiKey = preferences.getApiKey()
        val model = preferences.getModel()

        return translatorService.translateFormattedText(
            text = text,
            targetLanguage = targetLanguage,
            modelName = model,
            apiKey = apiKey,
            contextDescription = contextDescription,
            customInstruction = customInstruction,
            preset = preset
        )
    }
}
