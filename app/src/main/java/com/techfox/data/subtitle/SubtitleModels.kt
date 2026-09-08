package com.techfox.data.subtitle

import androidx.annotation.StringRes
import com.techfox.R
import java.util.UUID

enum class InputFormat(
    val displayName: String,
    val isSubtitle: Boolean,
    val extension: String,
    @StringRes val nameRes: Int
) {
    SUBTITLE_SRT("SRT Subtitle", true, "srt", R.string.format_subtitle_srt),
    SUBTITLE_VTT("WebVTT Subtitle", true, "vtt", R.string.format_subtitle_vtt),
    JSON("JSON Document", false, "json", R.string.format_json),
    MARKDOWN("Markdown Document", false, "md", R.string.format_markdown),
    LRC("LRC Lyrics", false, "lrc", R.string.format_lrc),
    PLAIN_TEXT("Plain Text", false, "txt", R.string.format_plain_text)
}

data class SubtitleCue(
    val id: Int,
    val startTime: String,
    val endTime: String,
    val rawTimingLine: String,
    val text: String,
    val isNoise: Boolean = false,
    val translatedText: String? = null
) {
    val effectiveTranslation: String
        get() = translatedText ?: text
}

data class SubtitleCharacter(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = ""
) {
    companion object {
        const val MAX_CHARACTERS_COUNT = 15 // Limit to 15 persons/characters in scene
    }
}
