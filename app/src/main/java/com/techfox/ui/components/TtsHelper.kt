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
    return when {
        clean == "auto" || clean.isBlank() -> {
            if (textToSpeak.isNotBlank()) detectLocaleFromText(textToSpeak) else Locale.getDefault()
        }
        clean == "vietnamese" || clean == "vi" || clean.startsWith("vi-") -> Locale.forLanguageTag("vi-VN")
        clean == "english" || clean == "en" || clean.startsWith("en-") -> Locale.ENGLISH
        clean == "spanish" || clean == "es" || clean.startsWith("es-") -> Locale.forLanguageTag("es-ES")
        clean == "french" || clean == "fr" || clean.startsWith("fr-") -> Locale.FRENCH
        clean == "german" || clean == "de" || clean.startsWith("de-") -> Locale.GERMAN
        clean == "japanese" || clean == "ja" || clean.startsWith("ja-") -> Locale.JAPANESE
        clean in listOf("chinese", "mandarin", "zh", "zh-cn", "simplified chinese") -> Locale.SIMPLIFIED_CHINESE
        clean in listOf("traditional chinese", "zh-tw", "zh-hk", "cantonese") -> Locale.TRADITIONAL_CHINESE
        clean == "korean" || clean == "ko" || clean.startsWith("ko-") -> Locale.KOREAN
        clean == "italian" || clean == "it" || clean.startsWith("it-") -> Locale.ITALIAN
        clean == "portuguese" || clean == "pt" || clean.startsWith("pt-") -> Locale.forLanguageTag("pt-PT")
        clean == "russian" || clean == "ru" || clean.startsWith("ru-") -> Locale.forLanguageTag("ru-RU")
        clean == "hindi" || clean == "hi" || clean.startsWith("hi-") -> Locale.forLanguageTag("hi-IN")
        clean == "arabic" || clean == "ar" || clean.startsWith("ar-") -> Locale.forLanguageTag("ar")
        clean == "thai" || clean == "th" || clean.startsWith("th-") -> Locale.forLanguageTag("th-TH")
        clean == "indonesian" || clean == "id" || clean.startsWith("id-") -> Locale.forLanguageTag("id-ID")
        else -> {
            try {
                val candidate = Locale.forLanguageTag(language)
                if (candidate.language.isNotBlank()) candidate else {
                    if (textToSpeak.isNotBlank()) detectLocaleFromText(textToSpeak) else Locale.getDefault()
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

