package com.techfox.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiTranslatorService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun translateWithNuance(
        text: String,
        targetLanguage: String,
        modelName: String,
        apiKey: String,
        customInstruction: String = "",
        enableInsight: Boolean = true
    ): Result<TranslationOutput> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()

        if (text.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter text to translate"))
        }

        // Condition 2: Check if input text is purely a URL -> reject translation
        if (TextSanitizer.isPureUrl(text)) {
            return@withContext Result.failure(IllegalArgumentException("URLs cannot be translated. Please enter text or sentences to translate."))
        }

        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Please enter your Gemini API Key in Settings to translate."))
        }

        // Condition 2 & 3: Smart format text (cleans spacing, excessive newlines, and replaces embedded URLs with [Link])
        val formattedText = TextSanitizer.smartFormat(text)
        if (formattedText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter text to translate"))
        }

        // Condition 1: Check if text is under 16 words -> skip insight generation
        val wordCount = TextSanitizer.countWords(formattedText)
        val isShortText = wordCount < 16

        val baseInstruction = if (isShortText) {
            if (customInstruction.isNotBlank()) {
                "${customInstruction.trim()}\nTarget Language: $targetLanguage\nIMPORTANT: Write the direct translation in $targetLanguage. Smartly format the translated text layout so it is clean and easy to read on screen (proper spacing, fixing awkward line breaks, clean punctuation) while keeping the exact translation meaning and value intact. Because the text is under 16 words, only output the direct translation without any cultural context or insights."
            } else {
                "You are an expert translator. Translate the following text into $targetLanguage.\nSmartly format the translated text layout so it is clean and easy to read on screen (proper spacing, fixing awkward line breaks, clean punctuation) while keeping the exact translation meaning and value intact.\nBecause this text is under 16 words, only output the direct translation in $targetLanguage without any cultural context or insights."
            }
        } else if (!enableInsight) {
            if (customInstruction.isNotBlank()) {
                "${customInstruction.trim()}\nTarget Language: $targetLanguage\nIMPORTANT: Write the direct translation and interactive key terms in $targetLanguage. Smartly format the translated text layout for effortless screen readability. Extract key terms (idioms, slang, collocations, vocabulary) with clear definitions. Skip the cultural context breakdown section to save tokens."
            } else {
                "You are an expert translator and linguist. Translate the following text into $targetLanguage.\nSmartly format the translated text layout so it is clean and easy to read on screen. Extract key vocabulary, idioms, slang, and phrases with clear explanations. Write the direct translation and key terms in $targetLanguage. Skip the cultural context breakdown section to save tokens."
            }
        } else {
            if (customInstruction.isNotBlank()) {
                "${customInstruction.trim()}\nTarget Language: $targetLanguage\nIMPORTANT: Write both the direct translation and the rich insights entirely in $targetLanguage. Smartly format the translated text layout for effortless screen readability. Extract abundant key terms (idioms, slang, collocations, cultural phrases, vocabulary) and provide deep, rich cultural nuance analysis."
            } else {
                "You are a master linguist, cultural anthropologist, and expert translator. Translate the following text into $targetLanguage.\nYou must thoroughly analyze linguistic nuances, idioms, slang, colloquialisms, politeness registers, emotional undertones, and cultural connotations. Smartly format the translated text layout so it is clean and easy to read on screen. Write the entire response, including translation, key terms, and cultural insights, entirely in $targetLanguage."
            }
        }

        val prompt = buildString {
            append(baseInstruction)
            append("\n\n")
            if (isShortText) {
                append("Provide your response strictly in the following format:\n")
                append("Direct Translation: [The most natural translation in $targetLanguage, formatted cleanly for screen readability, without surrounding bold or asterisks]\n\n")
            } else if (!enableInsight) {
                append("Provide your response strictly in the following format:\n")
                append("Direct Translation: [The most natural translation in $targetLanguage, formatted cleanly with natural paragraphs for screen readability, without surrounding bold or asterisks]\n\n")
                append("Key Terms:\n")
                append("[Extract multiple key terms, idioms, slang, phrasal verbs, collocations, expressive words, and cultural phrases from the text. Format each term on a separate line as:\n")
                append("- Translated Word/Phrase | Original Word/Phrase in source text | Type (Idiom/Slang/Cultural Nuance/Phrasal Verb/Key Vocabulary/Colloquialism) | Concise explanation in $targetLanguage\n")
                append("IMPORTANT: Ensure each Translated Word/Phrase is copied EXACTLY as it appears in your Direct Translation so it can be highlighted and clicked by the user.]\n\n")
            } else {
                append("Provide your response strictly in the following format:\n")
                append("Direct Translation: [The most natural translation in $targetLanguage, formatted cleanly with natural paragraphs for screen readability, without surrounding bold or asterisks]\n\n")
                append("Key Terms:\n")
                append("[Extract multiple key terms, idioms, slang, phrasal verbs, collocations, expressive words, and cultural phrases from the text (typically 3 to 8+ terms). Format each term on a separate line as:\n")
                append("- Translated Word/Phrase | Original Word/Phrase in source text | Type (Idiom/Slang/Cultural Nuance/Phrasal Verb/Key Vocabulary/Colloquialism) | Rich explanation in $targetLanguage (literal vs implied meaning, emotional tone/register, native usage context, and practical tips)\n")
                append("IMPORTANT: Ensure each Translated Word/Phrase is copied EXACTLY as it appears in your Direct Translation so it can be highlighted and clicked by the user.]\n\n")
                append("Cultural Context:\n")
                append("[Provide a deep, rich cultural breakdown formatted into 3-5 scannable Markdown bullet points written entirely in $targetLanguage. For example:\n")
                append("- **Bối cảnh & Ngữ cảnh giao tiếp / Context**: Practical situations where this is used, relationship dynamics (intimate, formal, social hierarchy, work vs casual)\n")
                append("- **Sắc thái & Cảm xúc ngầm / Nuance & Tone**: Underlying emotion, sarcasm, politeness level, empathy, humor, or unspoken intentions\n")
                append("- **Nguồn gốc văn hóa & So sánh / Cultural Background & Parallels**: Why native speakers express it this way, cultural origins, and equivalent concepts in other languages/dialects\n")
                append("- **Lưu ý sử dụng thực tế / Usage Tips**: How to use it naturally, common learner mistakes, and when to avoid it\n")
                append("Do NOT write a single dense paragraph or generic placeholder. Provide rich, highly informative, fascinating linguistic insights.]\n\n")
            }
            append("Text to translate:\n")
            append("\"\"\"$formattedText\"\"\"")
        }

        try {
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
                    put("temperature", 0.3)
                    put("topP", 0.9)
                }
                put("generationConfig", generationConfig)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$trimmedKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                // Parse error message if available
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }

                return@withContext Result.failure(Exception("Gemini API Error: $errorMsg"))
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val contentObj = firstCandidate?.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            val rawOutput = parts?.optJSONObject(0)?.optString("text").orEmpty()

            if (rawOutput.isBlank()) {
                return@withContext Result.failure(Exception("Received empty response from Gemini"))
            }

            val parsed = parseTranslation(
                rawText = rawOutput,
                sourceText = formattedText,
                targetLanguage = targetLanguage,
                isShortText = isShortText,
                enableInsight = enableInsight
            )
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.success(DEFAULT_MODELS)
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$trimmedKey&pageSize=100"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val parsed = parseModelsJson(responseBody)
            Result.success(parsed.ifEmpty { DEFAULT_MODELS })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        val DEFAULT_MODELS = listOf("gemini-3.1-flash-lite", "gemma-4-26b-a4b-it", "gemini-3.8-flash")

        private enum class Section { DIRECT, KEY_TERMS, CULTURAL }

        fun parseModelsJson(jsonStr: String): List<String> {
            if (jsonStr.isBlank()) return emptyList()
            return try {
                val json = JSONObject(jsonStr)
                val modelsArray = json.optJSONArray("models") ?: return emptyList()
                val result = mutableListOf<String>()
                for (i in 0 until modelsArray.length()) {
                    val obj = modelsArray.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "")
                    val supportedMethods = obj.optJSONArray("supportedGenerationMethods")
                    val supportsGenerateContent = if (supportedMethods != null) {
                        var hasMethod = false
                        for (j in 0 until supportedMethods.length()) {
                            if (supportedMethods.optString(j) == "generateContent") {
                                hasMethod = true
                                break
                            }
                        }
                        hasMethod
                    } else true

                    if (supportsGenerateContent && name.isNotBlank()) {
                        val cleanName = name.removePrefix("models/")
                        result.add(cleanName)
                    }
                }
                result
            } catch (e: Exception) {
                emptyList()
            }
        }
        fun parseTranslation(
            rawText: String,
            sourceText: String,
            targetLanguage: String,
            isShortText: Boolean = false,
            enableInsight: Boolean = true
        ): TranslationOutput {
            if (rawText.isBlank()) {
                return TranslationOutput(
                    sourceText = sourceText,
                    targetLanguage = targetLanguage,
                    directTranslation = "",
                    culturalContext = "",
                    keyTerms = emptyList()
                )
            }

            var currentSection = Section.DIRECT

            val directLines = mutableListOf<String>()
            val keyTermLines = mutableListOf<String>()
            val culturalLines = mutableListOf<String>()

            // Strict line-start anchored header matchers (supports optional #, **, colons, etc.)
            val directHeaderRegex = Regex("^(?:#{1,4}\\s*)?\\*{0,2}(?:Direct Translation|Translation)\\s*:?\\*{0,2}\\s*(.*)$", RegexOption.IGNORE_CASE)
            val keyTermsHeaderRegex = Regex("^(?:#{1,4}\\s*)?\\*{0,2}(?:Key Terms|Key Words|Interactive Terms|Vocabulary|Interactive Key Terms|Key Terms & Vocabulary|Key Terms / Vocabulary)\\s*:?\\*{0,2}\\s*(.*)$", RegexOption.IGNORE_CASE)
            val culturalHeaderRegex = Regex("^(?:#{1,4}\\s*)?\\*{0,2}(?:Cultural Context|Cultural Insights?|Insights?|Context|Linguistic & Cultural Context|Linguistic Nuances?|Linguistic Context)\\s*:?\\*{0,2}\\s*(.*)$", RegexOption.IGNORE_CASE)

            // Pattern for a key term line (e.g. "- Term | Original | Type | Explanation" or "• Term | Original | Explanation")
            val keyTermLinePattern = Regex("^(?:[-*•]|\\d+\\.)\\s*[^|]+\\|[^|]+(?:\\|.*)?$")

            val allLines = rawText.lines()

            for (line in allLines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) {
                    when (currentSection) {
                        Section.DIRECT -> if (directLines.isNotEmpty()) directLines.add("")
                        Section.KEY_TERMS -> {} // skip blank lines in key terms
                        Section.CULTURAL -> if (culturalLines.isNotEmpty()) culturalLines.add("")
                    }
                    continue
                }

                val directHeaderMatch = directHeaderRegex.matchEntire(trimmedLine)
                val keyTermsHeaderMatch = keyTermsHeaderRegex.matchEntire(trimmedLine)
                val culturalHeaderMatch = culturalHeaderRegex.matchEntire(trimmedLine)

                when {
                    directHeaderMatch != null -> {
                        currentSection = Section.DIRECT
                        val trailingContent = directHeaderMatch.groupValues.getOrNull(1)?.trim().orEmpty()
                            .removeSurrounding("**").removeSurrounding("*").removePrefix(":").removeSurrounding("**").removeSurrounding("*").trim()
                        if (trailingContent.isNotEmpty()) {
                            directLines.add(trailingContent)
                        }
                    }
                    keyTermsHeaderMatch != null -> {
                        currentSection = Section.KEY_TERMS
                        val trailingContent = keyTermsHeaderMatch.groupValues.getOrNull(1)?.trim().orEmpty()
                            .removeSurrounding("**").removeSurrounding("*").removePrefix(":").removeSurrounding("**").removeSurrounding("*").trim()
                        if (trailingContent.isNotEmpty() && !trailingContent.equals("None", ignoreCase = true)) {
                            keyTermLines.add(trailingContent)
                        }
                    }
                    culturalHeaderMatch != null -> {
                        currentSection = Section.CULTURAL
                        val trailingContent = culturalHeaderMatch.groupValues.getOrNull(1)?.trim().orEmpty()
                            .removeSurrounding("**").removeSurrounding("*").removePrefix(":").removeSurrounding("**").removeSurrounding("*").trim()
                        if (trailingContent.isNotEmpty()) {
                            culturalLines.add(trailingContent)
                        }
                    }
                    else -> {
                        if (keyTermLinePattern.matches(trimmedLine) && currentSection != Section.DIRECT) {
                            keyTermLines.add(trimmedLine)
                        } else {
                            when (currentSection) {
                                Section.DIRECT -> directLines.add(trimmedLine)
                                Section.KEY_TERMS -> {
                                    if (keyTermLinePattern.matches(trimmedLine)) {
                                        keyTermLines.add(trimmedLine)
                                    } else if (!trimmedLine.equals("None", ignoreCase = true) && !trimmedLine.startsWith("[") && !trimmedLine.endsWith("]")) {
                                        keyTermLines.add(trimmedLine)
                                    }
                                }
                                Section.CULTURAL -> culturalLines.add(trimmedLine)
                            }
                        }
                    }
                }
            }

            var direct = directLines.joinToString("\n").trim()
                .removeSurrounding("**").removeSurrounding("*").removePrefix(":").trim()
            var cultural = culturalLines.joinToString("\n").trim()
                .removePrefix(":").trim()

            if (isShortText || !enableInsight) {
                cultural = ""
            } else {
                if (cultural.isBlank()) cultural = "No additional insights detected."
            }

            val keyTermsList = mutableListOf<KeyTermInsight>()
            for (line in keyTermLines) {
                val cleanedLine = line.trim()
                    .removePrefix("-")
                    .removePrefix("*")
                    .removePrefix("•")
                    .replace(Regex("^\\d+\\.\\s*"), "")
                    .trim()
                if (cleanedLine.isBlank() || cleanedLine.equals("None", ignoreCase = true)) continue
                if (cleanedLine.startsWith("[") && cleanedLine.endsWith("]")) continue

                val parts = cleanedLine.split("|").map { it.trim().removeSurrounding("**").removeSurrounding("\"").removeSurrounding("'") }
                if (parts.size >= 4) {
                    val tTerm = parts[0].trim()
                    val oTerm = parts[1].trim()
                    val type = parts[2].trim().ifBlank { "Insight" }
                    val explanation = parts.subList(3, parts.size).joinToString(" | ").trim()
                    if (tTerm.isNotBlank() && explanation.isNotBlank()) {
                        keyTermsList.add(KeyTermInsight(translatedTerm = tTerm, originalTerm = oTerm, type = type, explanation = explanation))
                    }
                } else if (parts.size == 3) {
                    val tTerm = parts[0].trim()
                    val oTerm = parts[1].trim()
                    val explanation = parts[2].trim()
                    if (tTerm.isNotBlank() && explanation.isNotBlank()) {
                        keyTermsList.add(KeyTermInsight(translatedTerm = tTerm, originalTerm = oTerm, type = "Insight", explanation = explanation))
                    }
                } else if (parts.size == 2) {
                    val tTerm = parts[0].trim()
                    val explanation = parts[1].trim()
                    if (tTerm.isNotBlank() && explanation.isNotBlank()) {
                        keyTermsList.add(KeyTermInsight(translatedTerm = tTerm, originalTerm = "", type = "Insight", explanation = explanation))
                    }
                }
            }

            if (direct.isBlank()) direct = rawText.trim()

            return TranslationOutput(
                sourceText = sourceText,
                targetLanguage = targetLanguage,
                directTranslation = direct,
                culturalContext = cultural,
                keyTerms = keyTermsList
            )
        }
    }
}

data class TranslationOutput(
    val sourceText: String,
    val targetLanguage: String,
    val directTranslation: String,
    val culturalContext: String,
    val keyTerms: List<KeyTermInsight> = emptyList()
)
