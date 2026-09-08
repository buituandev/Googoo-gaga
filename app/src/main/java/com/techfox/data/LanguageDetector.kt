package com.techfox.data

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetector as LinguaDetector
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import com.techfox.ui.components.detectLocaleFromText
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device language detection using Lingua (FOSS) with heuristic script/character fallback.
 */
object LanguageDetector {
    private val linguaDetector: LinguaDetector by lazy {
        LanguageDetectorBuilder
            .fromAllSpokenLanguages()
            .withLowAccuracyMode()
            .build()
    }

    private val COMMON_ENGLISH_WORDS = setOf(
        "hello", "hi", "hey", "howdy", "greetings", "thanks", "thank", "bye", "goodbye",
        "good", "morning", "afternoon", "evening", "night", "yes", "no", "yeah", "yep",
        "nope", "okay", "ok", "please", "welcome", "sorry", "excuse", "world", "how",
        "what", "where", "when", "why", "who", "which", "is", "are", "am", "was", "were",
        "be", "been", "being", "the", "this", "that", "these", "those", "have", "has",
        "had", "do", "does", "did", "can", "could", "will", "would", "should", "love",
        "like", "friend", "people", "today", "tomorrow", "yesterday", "see", "you",
        "later", "again", "nice", "meet", "fine", "well", "great", "awesome", "cool",
        "test", "testing", "help", "translate", "translation", "language", "text",
        "name", "time", "day", "way", "man", "thing", "woman", "life", "child"
    )

    private val COMMON_VIETNAMESE_WORDS = setOf(
        "xin", "chao", "chào", "cam", "on", "cảm", "ơn", "tam", "biet", "tạm", "biệt",
        "vang", "vâng", "da", "dạ", "khong", "không", "duoc", "được", "tot", "tốt",
        "dep", "đẹp", "hom", "nay", "hôm", "ngay", "mai", "ngày", "o", "dau", "ở", "đâu",
        "ai", "gi", "gì", "sao", "nao", "nào", "the", "thế", "nhe", "nhé", "nha", "co", "có"
    )

    /**
     * Identifies the language of the given text using Lingua on-device model,
     * falling back to script/character heuristic analysis when Lingua returns UNKNOWN or encounters an error.
     *
     * @param text The input text/phrase to analyze.
     * @return BCP-47 language tag (e.g. "en", "vi", "fr", "es", "de", "ja", "ko", "zh", "ru", "ar", "th").
     */
    suspend fun identifyLanguage(text: String): String = withContext(Dispatchers.Default) {
        identify(text)
    }

    /**
     * Synchronous identification using common vocabulary fast-path, Lingua, and script/character fallback.
     */
    fun identify(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "en"

        // 1. Fast-path: check common dictionary words for short inputs (< 6 words)
        val words = trimmed.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (words.isNotEmpty() && words.size <= 5) {
            if (words.all { it in COMMON_ENGLISH_WORDS }) return "en"
            if (words.all { it in COMMON_VIETNAMESE_WORDS }) return "vi"
        }

        // 2. Lingua n-gram statistical detection
        try {
            val detected = linguaDetector.detectLanguageOf(trimmed)
            if (detected != Language.UNKNOWN) {
                val code = detected.isoCode639_1.name.lowercase(Locale.ROOT)
                if (code.isNotBlank() && code != "none") {
                    return code
                }
            }
        } catch (_: Throwable) {
            // Fallback gracefully on any Lingua exception
        }

        // 3. Script / character range heuristic fallback
        val fallbackLocale = detectLocaleFromText(trimmed)
        return fallbackLocale.language.ifBlank { "en" }
    }

    /**
     * Synchronous identification using script/character heuristic.
     */
    fun identifyLanguageFallback(text: String): String {
        return detectLocaleFromText(text).language.ifBlank { "en" }
    }

    /**
     * Normalizes a language name, display name, or BCP-47 code to a standard 2-letter ISO language code.
     * Supports all 110+ languages recognized by Android and ML Kit.
     */
    fun normalizeLanguageCode(lang: String): String {
        val clean = lang.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) return ""

        val base = clean.substringBefore("(").trim()

        // 1. Fast-path common alias lookup
        when {
            clean in listOf("en", "eng", "english", "tiếng anh", "tieng anh") || base == "english" -> return "en"
            clean in listOf("vi", "vie", "vietnamese", "tiếng việt", "tieng viet") || base == "vietnamese" -> return "vi"
            clean in listOf("fr", "fra", "fre", "french", "tiếng pháp", "tieng phap", "français", "francais") || base == "french" -> return "fr"
            clean in listOf("es", "spa", "spanish", "tiếng tây ban nha", "tieng tay ban nha", "español", "espanol") || base == "spanish" -> return "es"
            clean in listOf("de", "deu", "ger", "german", "tiếng đức", "tieng duc", "deutsch") || base == "german" -> return "de"
            clean in listOf("ja", "jpn", "japanese", "tiếng nhật", "tieng nhat", "nihongo", "日本語") || base == "japanese" -> return "ja"
            clean in listOf("zh", "zho", "chi", "chinese", "mandarin", "simplified chinese", "traditional chinese", "tiếng trung", "tieng trung", "中文") || base == "chinese" -> return "zh"
            clean in listOf("ko", "kor", "korean", "tiếng hàn", "tieng han", "한국어") || base == "korean" -> return "ko"
            clean in listOf("it", "ita", "italian", "tiếng ý", "tieng y", "italiano") || base == "italian" -> return "it"
            clean in listOf("pt", "por", "portuguese", "tiếng bồ đào nha", "tieng bo dao nha", "português", "portugues") || base == "portuguese" -> return "pt"
            clean in listOf("ru", "rus", "russian", "tiếng nga", "tieng nga", "русский") || base == "russian" -> return "ru"
            clean in listOf("hi", "hin", "hindi", "tiếng ấn độ", "tieng an do", "हिन्दी") || base == "hindi" -> return "hi"
            clean in listOf("ar", "ara", "arabic", "tiếng ả rập", "tieng a rap", "العربية") || base == "arabic" -> return "ar"
            clean in listOf("th", "tha", "thai", "tiếng thái", "tieng thai", "ไทย") || base == "thai" -> return "th"
            clean in listOf("id", "ind", "indonesian", "tiếng indonesia", "tieng indonesia", "bahasa indonesia") || base == "indonesian" -> return "id"
            clean in listOf("pl", "pol", "polish", "tiếng ba lan", "polski") || base == "polish" -> return "pl"
            clean in listOf("tr", "tur", "turkish", "tiếng thổ nhĩ kỳ", "türkçe") || base == "turkish" -> return "tr"
            clean in listOf("nl", "nld", "dutch", "tiếng hà lan", "nederlands") || base == "dutch" -> return "nl"
            clean in listOf("sv", "swe", "swedish", "tiếng thụy điển", "svenska") || base == "swedish" -> return "sv"
            clean in listOf("uk", "ukr", "ukrainian", "tiếng ukraina", "українська") || base == "ukrainian" -> return "uk"
            clean in listOf("cs", "ces", "czech", "tiếng séc", "čeština") || base == "czech" -> return "cs"
            clean in listOf("el", "ell", "greek", "tiếng hy lạp", "ελληνικά") || base == "greek" -> return "el"
            clean in listOf("he", "heb", "hebrew", "tiếng do thái", "עברית") || base == "hebrew" -> return "he"
            clean in listOf("da", "dan", "danish", "tiếng đan mạch", "dansk") || base == "danish" -> return "da"
            clean in listOf("fi", "fin", "finnish", "tiếng phần lan", "suomi") || base == "finnish" -> return "fi"
            clean in listOf("no", "nor", "norwegian", "tiếng na uy", "norsk") || base == "norwegian" -> return "no"
            clean in listOf("ro", "ron", "rum", "romanian", "tiếng romania", "română") || base == "romanian" -> return "ro"
            clean in listOf("hu", "hun", "hungarian", "tiếng hungary", "magyar") || base == "hungarian" -> return "hu"
            clean in listOf("fil", "tl", "filipino", "tagalog") || base in listOf("filipino", "tagalog") -> return "fil"
        }

        // 2. Direct BCP-47 tag resolution (e.g., "tr", "sv", "pl", "nl", "el", "he", "uk", "fi")
        try {
            val loc = Locale.forLanguageTag(clean)
            if (loc.language.isNotBlank() && loc.language.length in 2..3) {
                return loc.language
            }
        } catch (_: Exception) {}

        // 3. Dynamic lookup against all ISO languages (matches English names, native names, and display names)
        try {
            for (iso in Locale.getISOLanguages()) {
                val loc = Locale.forLanguageTag(iso)
                if (loc.language.equals(clean, ignoreCase = true) ||
                    loc.getDisplayLanguage(Locale.ENGLISH).equals(clean, ignoreCase = true) ||
                    loc.getDisplayLanguage(loc).equals(clean, ignoreCase = true) ||
                    loc.displayLanguage.equals(clean, ignoreCase = true) ||
                    loc.getDisplayLanguage(Locale.ENGLISH).equals(base, ignoreCase = true)
                ) {
                    return loc.language
                }
            }
        } catch (_: Exception) {}

        return clean
    }

    /**
     * Checks if the detected language matches the selected target language.
     */
    fun isSameLanguage(sourceLanguageTagOrName: String, targetLanguage: String): Boolean {
        val src = sourceLanguageTagOrName.trim().lowercase(Locale.ROOT)
        val tgt = targetLanguage.trim().lowercase(Locale.ROOT)
        if (src.isBlank() || tgt.isBlank() || src == "und" || src == "auto") return false

        if (src == tgt) return true

        val srcCode = normalizeLanguageCode(src)
        val tgtCode = normalizeLanguageCode(tgt)

        return srcCode.isNotBlank() && srcCode == tgtCode
    }

    /**
     * Checks if the text matches the selected target language.
     * Combines direct identification, target-normalized codes, and multi-candidate confidence evaluation.
     */
    fun matchesTargetLanguage(text: String, targetLanguage: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        val tgtCode = normalizeLanguageCode(targetLanguage)
        if (tgtCode.isBlank() || tgtCode == "auto") return false

        val identifiedCode = identify(trimmed)
        if (isSameLanguage(identifiedCode, targetLanguage)) return true

        // Check confidence scores for short or ambiguous texts
        try {
            val confidences = linguaDetector.computeLanguageConfidenceValues(trimmed)
            for ((lang, score) in confidences) {
                if (score < 0.75) break
                val langCode = lang.isoCode639_1.name.lowercase(Locale.ROOT)
                if (langCode == tgtCode) return true
            }
        } catch (_: Throwable) {}

        return false
    }
}
