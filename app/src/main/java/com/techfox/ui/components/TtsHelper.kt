package com.techfox.ui.components

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Controller for managing TextToSpeech playback state (playing, pausing/stopping, speaking).
 */
class TtsController(
    val isSpeaking: Boolean,
    val currentSpeakingText: String? = null,
    val toggle: (text: String, language: String) -> Unit
) {
    fun isSpeaking(text: String): Boolean = isSpeaking && currentSpeakingText == text
}

/**
 * Detects appropriate [Locale] based on script or character range for natural TTS pronunciation.
 */
fun detectLocaleFromText(text: String): Locale {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Locale.getDefault()
    return when {
        // Japanese: Hiragana or Katakana
        trimmed.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' } -> Locale.JAPANESE
        // Korean: Hangul Syllables or Jamo
        trimmed.any { it in '\uAC00'..'\uD7AF' || it in '\u1100'..'\u11FF' || it in '\u3130'..'\u318F' } -> Locale.KOREAN
        // Chinese / Hanzi: CJK Ideographs (when without Kana)
        trimmed.any { it in '\u4E00'..'\u9FFF' || it in '\u3400'..'\u4DBF' } -> Locale.SIMPLIFIED_CHINESE
        // Russian / Cyrillic
        trimmed.any { it in '\u0400'..'\u04FF' } -> Locale.forLanguageTag("ru-RU")
        // Arabic
        trimmed.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' } -> Locale.forLanguageTag("ar")
        // Thai
        trimmed.any { it in '\u0E00'..'\u0E7F' } -> Locale.forLanguageTag("th-TH")
        // Hindi / Devanagari
        trimmed.any { it in '\u0900'..'\u097F' } -> Locale.forLanguageTag("hi-IN")
        // Vietnamese specific diacritics
        trimmed.any { "đĐơƠưƯàáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộùúủũụỳýỷỹỵ".contains(it, ignoreCase = true) } -> Locale.forLanguageTag("vi-VN")
        else -> Locale.ENGLISH
    }
}

/**
 * Resolves a natural language name or tag to the appropriate [Locale] for TTS playback.
 */
fun resolveLocaleForLanguage(language: String, textToSpeak: String = ""): Locale {
    val clean = language.trim().lowercase(Locale.ROOT)
    val base = clean.substringBefore("(").trim()
    return when {
        clean == "auto" || clean.isBlank() -> {
            if (textToSpeak.isNotBlank()) detectLocaleFromText(textToSpeak) else Locale.getDefault()
        }
        clean in listOf("vietnamese", "vi", "tiếng việt") || clean.startsWith("vi-") || base == "vietnamese" -> Locale.forLanguageTag("vi-VN")
        clean in listOf("english", "en", "tiếng anh") || clean.startsWith("en-") || base == "english" -> Locale.ENGLISH
        clean in listOf("spanish", "es") || clean.startsWith("es-") || base == "spanish" -> Locale.forLanguageTag("es-ES")
        clean in listOf("french", "fr") || clean.startsWith("fr-") || base == "french" -> Locale.FRENCH
        clean in listOf("german", "de") || clean.startsWith("de-") || base == "german" -> Locale.GERMAN
        clean in listOf("japanese", "ja") || clean.startsWith("ja-") || base == "japanese" -> Locale.JAPANESE
        clean in listOf("chinese (simplified)", "simplified chinese") -> Locale.SIMPLIFIED_CHINESE
        clean in listOf("chinese (traditional)", "traditional chinese", "zh-tw", "zh-hk", "cantonese") -> Locale.TRADITIONAL_CHINESE
        clean in listOf("chinese", "mandarin", "zh", "zh-cn") || base == "chinese" -> Locale.SIMPLIFIED_CHINESE
        clean in listOf("korean", "ko") || clean.startsWith("ko-") || base == "korean" -> Locale.KOREAN
        clean in listOf("italian", "it") || clean.startsWith("it-") || base == "italian" -> Locale.ITALIAN
        clean in listOf("portuguese (brazil)", "pt-br") -> Locale.forLanguageTag("pt-BR")
        clean in listOf("portuguese (portugal)", "portuguese", "pt", "pt-pt") || base == "portuguese" -> Locale.forLanguageTag("pt-PT")
        clean in listOf("russian", "ru") || clean.startsWith("ru-") || base == "russian" -> Locale.forLanguageTag("ru-RU")
        clean in listOf("hindi", "hi") || clean.startsWith("hi-") || base == "hindi" -> Locale.forLanguageTag("hi-IN")
        clean in listOf("arabic", "ar") || clean.startsWith("ar-") || base == "arabic" -> Locale.forLanguageTag("ar")
        clean in listOf("thai", "th") || clean.startsWith("th-") || base == "thai" -> Locale.forLanguageTag("th-TH")
        clean in listOf("indonesian", "id") || clean.startsWith("id-") || base == "indonesian" -> Locale.forLanguageTag("id-ID")
        clean in listOf("polish", "pl") || base == "polish" -> Locale.forLanguageTag("pl-PL")
        clean in listOf("turkish", "tr") || base == "turkish" -> Locale.forLanguageTag("tr-TR")
        clean in listOf("dutch", "nl") || base == "dutch" -> Locale.forLanguageTag("nl-NL")
        clean in listOf("swedish", "sv") || base == "swedish" -> Locale.forLanguageTag("sv-SE")
        clean in listOf("greek", "el") || base == "greek" -> Locale.forLanguageTag("el-GR")
        clean in listOf("czech", "cs") || base == "czech" -> Locale.forLanguageTag("cs-CZ")
        clean in listOf("ukrainian", "uk") || base == "ukrainian" -> Locale.forLanguageTag("uk-UA")
        clean in listOf("danish", "da") || base == "danish" -> Locale.forLanguageTag("da-DK")
        clean in listOf("finnish", "fi") || base == "finnish" -> Locale.forLanguageTag("fi-FI")
        clean in listOf("norwegian", "no") || base == "norwegian" -> Locale.forLanguageTag("no-NO")
        clean in listOf("romanian", "ro") || base == "romanian" -> Locale.forLanguageTag("ro-RO")
        clean in listOf("hungarian", "hu") || base == "hungarian" -> Locale.forLanguageTag("hu-HU")
        else -> {
            try {
                val normalizedCode = com.techfox.data.LanguageDetector.normalizeLanguageCode(language)
                if (normalizedCode.isNotBlank() && normalizedCode.length in 2..3) {
                    Locale.forLanguageTag(normalizedCode)
                } else if (textToSpeak.isNotBlank()) {
                    detectLocaleFromText(textToSpeak)
                } else {
                    Locale.getDefault()
                }
            } catch (_: Exception) {
                if (textToSpeak.isNotBlank()) detectLocaleFromText(textToSpeak) else Locale.getDefault()
            }
        }
    }
}

/**
 * Remembers and manages the lifecycle of a [TextToSpeech] instance with play/pause/stop toggling support.
 */
@Composable
fun rememberTtsController(): TtsController {
    val context = LocalContext.current
    var isSpeaking by remember { mutableStateOf(false) }
    var currentSpeakingText by remember { mutableStateOf<String?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(context) {
        val ttsInstance = TextToSpeech(context) { /* Ready */ }
        ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post { isSpeaking = true }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    isSpeaking = false
                    currentSpeakingText = null
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    isSpeaking = false
                    currentSpeakingText = null
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post {
                    isSpeaking = false
                    currentSpeakingText = null
                }
            }
        })
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    return remember(tts, isSpeaking, currentSpeakingText) {
        TtsController(
            isSpeaking = isSpeaking,
            currentSpeakingText = currentSpeakingText,
            toggle = { text, language ->
                if (isSpeaking && currentSpeakingText == text) {
                    tts?.stop()
                    isSpeaking = false
                    currentSpeakingText = null
                } else if (text.isNotBlank()) {
                    tts?.stop()
                    currentSpeakingText = text
                    isSpeaking = true

                    val clean = language.trim().lowercase(Locale.ROOT)
                    val locale = if (clean == "auto" || clean.isBlank()) {
                        val detectedCode = com.techfox.data.LanguageDetector.identify(text)
                        resolveLocaleForLanguage(detectedCode, text)
                    } else {
                        resolveLocaleForLanguage(language, text)
                    }
                    tts?.language = locale
                    val utteranceId = "TtsUtterance_${System.currentTimeMillis()}"
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                }
            }
        )
    }
}

