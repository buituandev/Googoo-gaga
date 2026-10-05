package com.techfox.data.subtitle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SubtitleTranslatorService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Translates subtitle cues using silence-aware smart chunking, compact Key-Value JSON payload,
     * sliding context window, token-guard noise filtering, and micro-retry error recovery.
     */
    suspend fun translateSubtitleInChunks(
        cues: List<SubtitleCue>,
        targetLanguage: String,
        modelName: String,
        apiKey: String,
        contextDescription: String,
        characters: List<SubtitleCharacter>,
        customInstruction: String = "",
        preset: SubtitlePreset = SubtitlePreset.DEFAULT_PRESET,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        startingChunkIndex: Int = 0,
        enableTokenGuard: Boolean = true,
        onChunkProgress: suspend (chunkIndex: Int, totalChunks: Int, currentCues: List<SubtitleCue>) -> Unit
    ): Result<List<SubtitleCue>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Please enter your Gemini API Key in Settings to translate."))
        }

        if (cues.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No subtitle cues found to translate."))
        }

        // Initialize mutable cue list with noise lines auto-handled if token guard is enabled
        val workingCues = cues.map { cue ->
            if (enableTokenGuard && cue.isNoise && cue.translatedText == null) {
                // Keep original sound or whimpering in final file without sending to AI
                cue.copy(isNoise = true, translatedText = cue.text)
            } else if (!enableTokenGuard) {
                // When Token Guard is off, do not treat cues as noise-guarded
                cue.copy(isNoise = false)
            } else {
                cue
            }
        }.toMutableList()

        // Identify all cues in the file that are translatable
        val allTranslatableIndices = workingCues.indices.filter {
            if (enableTokenGuard) {
                !workingCues[it].isNoise
            } else {
                true
            }
        }
        if (allTranslatableIndices.isEmpty()) {
            onChunkProgress(1, 1, workingCues)
            return@withContext Result.success(workingCues)
        }

        // Partition ALL translatable cues into deterministic smart chunks
        val chunks = createSmartChunks(
            translatableIndices = allTranslatableIndices,
            allCues = workingCues,
            minChunkSize = MIN_CHUNK_SIZE,
            maxChunkSize = MAX_CHUNK_SIZE,
            silenceThresholdMs = SILENCE_THRESHOLD_MS
        )
        val totalChunks = chunks.size.coerceAtLeast(1)

        // Find the actual first chunk that has incomplete translations
        val firstIncompleteChunkIndex = chunks.indexOfFirst { chunkIndices ->
            chunkIndices.any { workingCues[it].translatedText == null }
        }

        // If all chunks are already completed, report full progress and return
        if (firstIncompleteChunkIndex == -1) {
            onChunkProgress(totalChunks, totalChunks, workingCues)
            return@withContext Result.success(workingCues)
        }

        val startFromChunk = if (startingChunkIndex in 0 until totalChunks && startingChunkIndex <= firstIncompleteChunkIndex) {
            firstIncompleteChunkIndex
        } else {
            firstIncompleteChunkIndex
        }

        // Sliding context window: tracks up to ROLLING_CONTEXT_SIZE previous translated cues
        val rollingContext = mutableListOf<SubtitleCue>()

        // Pre-populate rolling context if resuming from a later chunk
        if (startFromChunk > 0) {
            val previousCues = workingCues.filter {
                it.translatedText != null && (!enableTokenGuard || !it.isNoise)
            }
            rollingContext.addAll(previousCues.takeLast(ROLLING_CONTEXT_SIZE))
        }

        for (chunkIdx in startFromChunk until totalChunks) {
            val cueIndicesInChunk = chunks[chunkIdx]
            val cuesToTranslate = cueIndicesInChunk.map { workingCues[it] }

            // If all cues in this chunk are already translated, report and continue
            if (cuesToTranslate.all { it.translatedText != null }) {
                onChunkProgress(chunkIdx + 1, totalChunks, workingCues.toList())
                continue
            }

            val chunkResult = translateCueChunk(
                cuesToTranslate = cuesToTranslate,
                rollingContext = rollingContext,
                targetLanguage = targetLanguage,
                modelName = modelName,
                apiKey = trimmedKey,
                contextDescription = contextDescription,
                characters = characters,
                customInstruction = customInstruction,
                preset = preset
            )

            if (chunkResult.isFailure) {
                return@withContext Result.failure(
                    chunkResult.exceptionOrNull() ?: Exception("Failed at chunk ${chunkIdx + 1}/$totalChunks")
                )
            }

            val translatedChunk = chunkResult.getOrThrow()
            for (translatedCue in translatedChunk) {
                val indexInWorking = workingCues.indexOfFirst { it.id == translatedCue.id }
                if (indexInWorking != -1) {
                    workingCues[indexInWorking] = translatedCue
                }
            }

            // Update rolling context with latest translated cues from this chunk
            rollingContext.clear()
            val recentTranslated = workingCues.filter {
                it.translatedText != null && (!enableTokenGuard || !it.isNoise)
            }
            rollingContext.addAll(recentTranslated.takeLast(ROLLING_CONTEXT_SIZE))

            // Report progress for smart pickup auto-saving
            onChunkProgress(chunkIdx + 1, totalChunks, workingCues.toList())
        }

        Result.success(workingCues)
    }

    /**
     * Translates a single chunk of cues with compact Key-Value JSON formatting,
     * sliding context, and automated micro-retry for any dropped keys.
     */
    private fun translateCueChunk(
        cuesToTranslate: List<SubtitleCue>,
        rollingContext: List<SubtitleCue>,
        targetLanguage: String,
        modelName: String,
        apiKey: String,
        contextDescription: String,
        characters: List<SubtitleCharacter>,
        customInstruction: String = "",
        preset: SubtitlePreset = SubtitlePreset.DEFAULT_PRESET
    ): Result<List<SubtitleCue>> {
        val prompt = preset.buildSubtitlePrompt(
            targetLanguage = targetLanguage,
            contextDescription = contextDescription,
            customInstruction = customInstruction,
            characters = characters,
            rollingContext = rollingContext,
            cuesToTranslate = cuesToTranslate
        )

        return try {
            val responseText = callGeminiRaw(prompt, modelName, apiKey)
            val parsedMap = parseAiSubtitleResponse(responseText).toMutableMap()

            // Missing Key Detection & Automated Micro-Retry
            val missingCues = cuesToTranslate.filter { !parsedMap.containsKey(it.id) || parsedMap[it.id].isNullOrBlank() }
            if (missingCues.isNotEmpty()) {
                try {
                    val retryPrompt = preset.buildMicroRetryPrompt(
                        targetLanguage = targetLanguage,
                        missingCues = missingCues
                    )
                    val retryResponse = callGeminiRaw(retryPrompt, modelName, apiKey)
                    val retryMap = parseAiSubtitleResponse(retryResponse)
                    for ((k, v) in retryMap) {
                        if (v.isNotBlank()) {
                            parsedMap[k] = v
                        }
                    }
                } catch (_: Exception) {
                    // Fallback gracefully to original text if micro-retry fails
                }
            }

            // Reassemble cues with 100% timestamp invariance
            val resultCues = cuesToTranslate.map { originalCue ->
                val translatedText = parsedMap[originalCue.id]?.trim()
                if (!translatedText.isNullOrBlank()) {
                    originalCue.copy(translatedText = translatedText, isNoise = false)
                } else {
                    // Fallback to original text if AI still dropped the cue ID
                    originalCue.copy(translatedText = originalCue.text, isNoise = false)
                }
            }

            Result.success(resultCues)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Translates formatted text (JSON, Markdown, LRC, or plain text) with strict
     * formatting and syntax preservation.
     */
    suspend fun translateFormattedText(
        text: String,
        targetLanguage: String,
        modelName: String,
        apiKey: String,
        contextDescription: String,
        customInstruction: String = "",
        preset: SubtitlePreset = SubtitlePreset.DEFAULT_PRESET
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Please enter your Gemini API Key in Settings to translate."))
        }

        if (text.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter or paste text to translate."))
        }

        val prompt = preset.buildFormattedTextPrompt(
            text = text,
            targetLanguage = targetLanguage,
            contextDescription = contextDescription,
            customInstruction = customInstruction
        )

        try {
            val response = callGeminiRaw(prompt, modelName, apiKey)
            Result.success(response.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Raw Gemini API request execution with safe OkHttp resource management.
     */
    private fun callGeminiRaw(prompt: String, modelName: String, apiKey: String): String {
        val jsonBody = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().put(JSONObject().put("text", prompt))
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)

            val generationConfig = JSONObject().apply {
                put("temperature", 0.65)
                put("topP", 0.95)
            }
            put("generationConfig", generationConfig)
        }

        val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
        val url = "$BASE_API_URL/models/$modelName:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (_: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                throw Exception("Gemini API Error: $errorMsg")
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val contentObj = firstCandidate?.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            val rawOutput = parts?.optJSONObject(0)?.optString("text").orEmpty()

            if (rawOutput.isBlank()) {
                throw Exception("Received empty response from Gemini")
            }

            return rawOutput
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 30
        const val MIN_CHUNK_SIZE = 20
        const val MAX_CHUNK_SIZE = 35
        const val SILENCE_THRESHOLD_MS = 2500L
        const val ROLLING_CONTEXT_SIZE = 5
        private const val BASE_API_URL = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val FALLBACK_KV_REGEX = Regex(
            "\"(?:cue_)?(\\d+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        )
        private val FALLBACK_ARRAY_REGEX = Regex(
            "\"id\"\\s*:\\s*(\\d+).*?\"(?:translated|text)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            RegexOption.DOT_MATCHES_ALL
        )

        /**
         * Partitions translatable cue indices into smart chunks by detecting natural
         * conversational pauses (silence gap >= silenceThresholdMs) between cues.
         */
        fun createSmartChunks(
            translatableIndices: List<Int>,
            allCues: List<SubtitleCue>,
            minChunkSize: Int = MIN_CHUNK_SIZE,
            maxChunkSize: Int = MAX_CHUNK_SIZE,
            silenceThresholdMs: Long = SILENCE_THRESHOLD_MS
        ): List<List<Int>> {
            if (translatableIndices.isEmpty()) return emptyList()
            val chunks = mutableListOf<List<Int>>()
            var currentChunk = mutableListOf<Int>()

            for (i in translatableIndices.indices) {
                val cueIndex = translatableIndices[i]
                currentChunk.add(cueIndex)

                if (currentChunk.size >= minChunkSize) {
                    val isLast = (i == translatableIndices.size - 1)
                    if (!isLast) {
                        val nextCueIndex = translatableIndices[i + 1]
                        val currentCue = allCues[cueIndex]
                        val nextCue = allCues[nextCueIndex]
                        val silenceGap = nextCue.startMs - currentCue.endMs
                        val reachedMax = currentChunk.size >= maxChunkSize

                        if (silenceGap >= silenceThresholdMs || reachedMax) {
                            chunks.add(currentChunk.toList())
                            currentChunk = mutableListOf()
                        }
                    } else {
                        chunks.add(currentChunk.toList())
                        currentChunk = mutableListOf()
                    }
                }
            }

            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toList())
            }

            return chunks
        }

        /**
         * Robust parser for AI subtitle response supporting JSON Object {"1": "...", "2": "..."},
         * JSON Array [{"id": 1, "translated": "..."}], and regex fallbacks.
         */
        fun parseAiSubtitleResponse(rawOutput: String): Map<Int, String> {
            val cleanJson = cleanMarkdownCodeBlocks(rawOutput)
            val map = mutableMapOf<Int, String>()

            // 1. Try parsing as JSON Object {"1": "...", "2": "..."}
            try {
                val jsonObject = JSONObject(cleanJson)
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next()
                    val id = rawKey.trim().removePrefix("cue_").removePrefix("cue").removePrefix("id_").toIntOrNull() ?: continue
                    val text = jsonObject.optString(rawKey, "")
                    map[id] = text
                }
                if (map.isNotEmpty()) return map
            } catch (_: Exception) {
                // Fall through
            }

            // 2. Try parsing as JSON Array [{"id": 1, "translated": "..."}, ...] (legacy / alternative)
            try {
                val jsonArray = JSONArray(cleanJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optInt("id", -1)
                    if (id != -1) {
                        val text = obj.optString("translated", obj.optString("text", ""))
                        map[id] = text
                    }
                }
                if (map.isNotEmpty()) return map
            } catch (_: Exception) {
                // Fall through
            }

            // 3. Fallback Regex for Key-Value JSON ("1": "...")
            val kvMatches = FALLBACK_KV_REGEX.findAll(cleanJson)
            for (m in kvMatches) {
                val id = m.groupValues[1].toIntOrNull() ?: continue
                val text = unescapeJsonString(m.groupValues[2])
                map[id] = text
            }
            if (map.isNotEmpty()) return map

            // 4. Fallback Regex for Array JSON ("id": 1 ... "translated": "...")
            val arrayMatches = FALLBACK_ARRAY_REGEX.findAll(cleanJson)
            for (m in arrayMatches) {
                val id = m.groupValues[1].toIntOrNull() ?: continue
                val text = unescapeJsonString(m.groupValues[2])
                map[id] = text
            }

            return map
        }

        fun unescapeJsonString(str: String): String {
            return str
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        }

        fun cleanMarkdownCodeBlocks(text: String): String {
            var clean = text.trim()
            val codeFenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
            val match = codeFenceRegex.find(clean)
            if (match != null) {
                clean = match.groupValues[1].trim()
            } else {
                if (clean.startsWith("```json", ignoreCase = true)) {
                    clean = clean.removePrefix("```json").removePrefix("```JSON")
                } else if (clean.startsWith("```")) {
                    clean = clean.removePrefix("```")
                }
                if (clean.endsWith("```")) {
                    clean = clean.removeSuffix("```")
                }
            }
            return clean.trim()
        }
    }
}
