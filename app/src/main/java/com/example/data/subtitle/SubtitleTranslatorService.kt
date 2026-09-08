package com.example.data.subtitle

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
     * Translates subtitle cues in chunks (30-40 cues per chunk), preserving rolling context,
     * character information, and filtering noise tokens.
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
        chunkSize: Int = 35,
        startingChunkIndex: Int = 0,
        onChunkProgress: suspend (chunkIndex: Int, totalChunks: Int, currentCues: List<SubtitleCue>) -> Unit
    ): Result<List<SubtitleCue>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Please enter your Gemini API Key in Settings to translate."))
        }

        if (cues.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No subtitle cues found to translate."))
        }

        // Initialize mutable cue list with noise lines auto-handled
        val workingCues = cues.map { cue ->
            if (cue.isNoise && cue.translatedText == null) {
                // Keep original sound or whimpering in final file without sending to AI
                cue.copy(translatedText = cue.text)
            } else {
                cue
            }
        }.toMutableList()

        // Identify cues that require AI translation
        val translatableIndices = workingCues.indices.filter { !workingCues[it].isNoise && workingCues[it].translatedText == null }
        if (translatableIndices.isEmpty()) {
            onChunkProgress(1, 1, workingCues)
            return@withContext Result.success(workingCues)
        }

        // Partition translatable cues into chunks of `chunkSize`
        val chunks = translatableIndices.chunked(chunkSize)
        val totalChunks = chunks.size

        // Rolling context window: tracks up to 3 previous translated cues
        val rollingContext = mutableListOf<SubtitleCue>()

        // Pre-populate rolling context if resuming from a later chunk
        if (startingChunkIndex > 0) {
            val previousCues = workingCues.filter { it.translatedText != null && !it.isNoise }
            rollingContext.addAll(previousCues.takeLast(3))
        }

        for (chunkIdx in startingChunkIndex until totalChunks) {
            val cueIndicesInChunk = chunks[chunkIdx]
            val cuesToTranslate = cueIndicesInChunk.map { workingCues[it] }

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
                return@withContext Result.failure(chunkResult.exceptionOrNull() ?: Exception("Failed at chunk ${chunkIdx + 1}/$totalChunks"))
            }

            val translatedChunk = chunkResult.getOrThrow()
            for (translatedCue in translatedChunk) {
                val indexInWorking = workingCues.indexOfFirst { it.id == translatedCue.id }
                if (indexInWorking != -1) {
                    workingCues[indexInWorking] = translatedCue
                }
            }

            // Update rolling context with latest 3 translated cues from this chunk
            rollingContext.clear()
            val recentTranslated = workingCues.filter { it.translatedText != null && !it.isNoise }
            rollingContext.addAll(recentTranslated.takeLast(3))

            // Report progress for smart pickup auto-saving
            onChunkProgress(chunkIdx + 1, totalChunks, workingCues.toList())
        }

        Result.success(workingCues)
    }

    /**
     * Translates a single chunk of cues with tone preset polish and validates AI response structure.
     */
    private suspend fun translateCueChunk(
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
            val parsedMap = parseAiSubtitleResponse(responseText)

            // Double check: Validate that all requested cue IDs were returned
            val resultCues = cuesToTranslate.map { originalCue ->
                val translatedText = parsedMap[originalCue.id]?.trim()
                if (!translatedText.isNullOrBlank()) {
                    originalCue.copy(translatedText = translatedText)
                } else {
                    // Fallback to original text if AI dropped the specific cue ID
                    originalCue.copy(translatedText = originalCue.text)
                }
            }

            Result.success(resultCues)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Translates formatted text (JSON, Markdown, LRC, or plain text) while enforcing
     * "remain the format of the text pasted" and using the selected preset.
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
     * Raw Gemini API request execution.
     */
    private fun callGeminiRaw(prompt: String, modelName: String, apiKey: String): String {
        val jsonBody = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    }
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

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body.string()

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errJson = JSONObject(responseBody)
                errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
            } catch (e: Exception) {
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

    /**
     * Parses the AI response into a map of ID -> Translated Text.
     */
    private fun parseAiSubtitleResponse(rawOutput: String): Map<Int, String> {
        val cleanJson = cleanMarkdownCodeBlocks(rawOutput)
        val map = mutableMapOf<Int, String>()

        try {
            val jsonArray = JSONArray(cleanJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getInt("id")
                val text = obj.optString("translated", obj.optString("text", ""))
                map[id] = text
            }
            return map
        } catch (e: Exception) {
            // Fallback line-by-line parsing if JSON is malformed
            val idRegex = Regex("\"id\"\\s*:\\s*(\\d+).*?\"translated\"\\s*:\\s*\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)
            val matches = idRegex.findAll(rawOutput)
            for (m in matches) {
                val id = m.groupValues[1].toIntOrNull() ?: continue
                val text = m.groupValues[2].replace("\\n", "\n").replace("\\\"", "\"")
                map[id] = text
            }
            return map
        }
    }

    private fun cleanMarkdownCodeBlocks(text: String): String {
        var clean = text.trim()
        if (clean.startsWith("```json", ignoreCase = true)) {
            clean = clean.removePrefix("```json").removePrefix("```JSON")
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```")
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```")
        }
        return clean.trim()
    }
}
