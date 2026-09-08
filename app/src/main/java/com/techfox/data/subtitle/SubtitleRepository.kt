package com.techfox.data.subtitle

import android.content.Context
import com.techfox.data.AppDatabase
import com.techfox.data.PreferencesManager
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

class SubtitleRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val jobDao = db.subtitleJobDao()
    private val translatorService = SubtitleTranslatorService()
    val preferences = PreferencesManager(context)

    val activeJobs: Flow<List<SubtitleJobEntity>> = jobDao.getAllJobs()

    suspend fun getLatestActiveJob(): SubtitleJobEntity? = jobDao.getLatestActiveJob()

    suspend fun getJobById(jobId: String): SubtitleJobEntity? = jobDao.getJobById(jobId)

    suspend fun deleteJob(jobId: String) = jobDao.deleteJobById(jobId)

    /**
     * Compute a deterministic unique Job ID based on the file content/name and target language,
     * preventing collision across different files while enabling instant pickup for the same file.
     */
    fun computeJobId(content: String, fileName: String, targetLang: String): String {
        val raw = "$fileName:$targetLang:${content.take(500)}:${content.length}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
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
        onProgress: suspend (currentChunk: Int, totalChunks: Int, completedCues: Int, totalCues: Int, noiseCues: Int) -> Unit
    ): Result<List<SubtitleCue>> {
        val apiKey = preferences.getApiKey()
        val model = preferences.getModel()

        // Check if there is an existing job to resume from
        val existingJob = jobDao.getJobById(jobId)
        val startingChunk = if (existingJob != null && existingJob.status == "PAUSED") {
            existingJob.completedChunks
        } else {
            0
        }

        val workingCues = if (existingJob != null && existingJob.cuesJson.isNotBlank() && startingChunk > 0) {
            val savedCues = SubtitleJobEntity.deserializeCues(existingJob.cuesJson)
            if (savedCues.size == cues.size) savedCues else cues
        } else {
            cues
        }

        val noiseCount = workingCues.count { it.isNoise }
        val translatableCount = workingCues.size - noiseCount
        val estimatedTotalChunks = ((translatableCount + 34) / 35).coerceAtLeast(1)

        // Save initial job state as IN_PROGRESS
        val initialJob = SubtitleJobEntity(
            jobId = jobId,
            fileName = fileName,
            format = format.name,
            targetLanguage = targetLanguage,
            contextDescription = contextDescription,
            charactersJson = SubtitleJobEntity.serializeCharacters(characters),
            originalContent = "",
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
                val pausedJob = initialJob.copy(
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
            // Save state as PAUSED so user can resume at the right chunk without conflicts
            val pausedJob = initialJob.copy(
                status = "PAUSED",
                errorMessage = error.localizedMessage
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
