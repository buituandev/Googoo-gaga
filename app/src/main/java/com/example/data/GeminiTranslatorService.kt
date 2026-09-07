package com.example.data

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

    companion object {
        fun parseTranslation(
            rawText: String,
            sourceText: String,
            targetLanguage: String,
            isShortText: Boolean = false,
            enableInsight: Boolean = true
        ): TranslationOutput {
            var direct = ""
            var keyTermsRaw = ""
            var cultural = ""
            val keyTermsList = mutableListOf<KeyTermInsight>()

            val directRegex = Regex("(?i)\\*{0,2}(?:Direct Translation|Translation):?\\*{0,2}\\s*")
            val keyTermsRegex = Regex("(?i)\\*{0,2}(?:Key Terms|Key Words|Vocabulary|Interactive Terms):?\\*{0,2}\\s*")
            val culturalRegex = Regex("(?i)\\*{0,2}(?:Cultural Context|Cultural Insights?|Insights?|Context):?\\*{0,2}\\s*")

            val directMatch = directRegex.find(rawText)
            val keyTermsMatch = keyTermsRegex.find(rawText)
            val culturalMatch = culturalRegex.find(rawText)

            if (directMatch != null) {
                val directStart = directMatch.range.last + 1
                val directEnd = keyTermsMatch?.range?.first ?: culturalMatch?.range?.first ?: rawText.length
                direct = rawText.substring(directStart, directEnd).trim()

                if (keyTermsMatch != null) {
                    val keyTermsStart = keyTermsMatch.range.last + 1
                    val keyTermsEnd = culturalMatch?.range?.first ?: rawText.length
                    keyTermsRaw = rawText.substring(keyTermsStart, keyTermsEnd).trim()
                }

                if (culturalMatch != null) {
                    val culturalStart = culturalMatch.range.last + 1
                    cultural = rawText.substring(culturalStart).trim()
                }
            } else {
                val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isNotEmpty()) {
                    direct = lines[0].replace(Regex("^\\*{0,2}(Direct Translation|Translation):?\\*{0,2}\\s*", RegexOption.IGNORE_CASE), "")
                    cultural = if (isShortText || !enableInsight) "" else lines.drop(1).joinToString("\n\n").replace(Regex("^\\*{0,2}(?:Cultural Context|Cultural Insights?|Insights?|Context):?\\*{0,2}\\s*", RegexOption.IGNORE_CASE), "")
                }
            }

            // Clean any leftover markdown wrapper asterisks on direct translation
            direct = direct.trim().removeSurrounding("**").removeSurrounding("*").removePrefix(":").trim()
            cultural = cultural.trim().removePrefix(":").trim()

            if (isShortText || !enableInsight) {
                cultural = ""
            } else {
                if (cultural.isBlank()) cultural = "No additional insights detected."
            }

            // Parse keyTermsRaw into List<KeyTermInsight>
            if (keyTermsRaw.isNotBlank() && !keyTermsRaw.equals("None", ignoreCase = true)) {
                val termLines = keyTermsRaw.lines()
                for (line in termLines) {
                    val cleanedLine = line.trim()
                        .removePrefix("-")
                        .removePrefix("*")
                        .removePrefix("•")
                        .replace(Regex("^\\d+\\.\\s*"), "")
                        .trim()
                    if (cleanedLine.isBlank() || cleanedLine.equals("None", ignoreCase = true)) continue

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
