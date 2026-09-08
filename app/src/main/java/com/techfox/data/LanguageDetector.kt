package com.example.data

import com.example.ui.components.detectLocaleFromText
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import java.util.Locale
import kotlinx.coroutines.tasks.await

/**
 * On-device language detection using Google ML Kit with heuristic script/character fallback.
 */
object LanguageDetector {
    private val mlKitIdentifier: LanguageIdentifier by lazy {
        LanguageIdentification.getClient()
    }

    /**
     * Identifies the language of the given text using Google ML Kit on-device model,
     * falling back to script/character heuristic analysis when ML Kit returns undetermined ("und") or encounters an error.
     *
     * @param text The input text/phrase to analyze.
     * @return BCP-47 language tag (e.g. "en", "vi", "fr", "es", "de", "ja", "ko", "zh", "ru", "ar", "th").
     */
    suspend fun identifyLanguage(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "en"

        try {
            val languageCode = mlKitIdentifier.identifyLanguage(trimmed).await()
            if (languageCode != null && languageCode != "und" && languageCode.isNotBlank()) {
                return languageCode
            }
        } catch (_: Throwable) {
            // Fallback gracefully on any ML Kit initialization / task exception
        }

        // Script / character range heuristic fallback
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

        // 1. Fast-path common alias lookup
        when (clean) {
            in listOf("en", "eng", "english", "tiếng anh", "tieng anh") -> return "en"
            in listOf("vi", "vie", "vietnamese", "tiếng việt", "tieng viet") -> return "vi"
            in listOf("fr", "fra", "fre", "french", "tiếng pháp", "tieng phap", "français", "francais") -> return "fr"
            in listOf("es", "spa", "spanish", "tiếng tây ban nha", "tieng tay ban nha", "español", "espanol") -> return "es"
            in listOf("de", "deu", "ger", "german", "tiếng đức", "tieng duc", "deutsch") -> return "de"
            in listOf("ja", "jpn", "japanese", "tiếng nhật", "tieng nhat", "nihongo", "日本語") -> return "ja"
            in listOf("zh", "zho", "chi", "chinese", "mandarin", "simplified chinese", "traditional chinese", "tiếng trung", "tieng trung", "中文") -> return "zh"
            in listOf("ko", "kor", "korean", "tiếng hàn", "tieng han", "한국어") -> return "ko"
            in listOf("it", "ita", "italian", "tiếng ý", "tieng y", "italiano") -> return "it"
            in listOf("pt", "por", "portuguese", "tiếng bồ đào nha", "tieng bo dao nha", "português", "portugues") -> return "pt"
            in listOf("ru", "rus", "russian", "tiếng nga", "tieng nga", "русский") -> return "ru"
            in listOf("hi", "hin", "hindi", "tiếng ấn độ", "tieng an do", "हिन्दी") -> return "hi"
            in listOf("ar", "ara", "arabic", "tiếng ả rập", "tieng a rap", "العربية") -> return "ar"
            in listOf("th", "tha", "thai", "tiếng thái", "tieng thai", "ไทย") -> return "th"
            in listOf("id", "ind", "indonesian", "tiếng indonesia", "tieng indonesia", "bahasa indonesia") -> return "id"
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
                    loc.displayLanguage.equals(clean, ignoreCase = true)
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
}
