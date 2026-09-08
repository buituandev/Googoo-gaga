package com.techfox.data

/**
 * Utility for sanitizing URLs, smart-formatting text (collapsing excessive spaces & line breaks),
 * and enforcing translation conditions.
 */
object TextSanitizer {
    val URL_REGEX = Regex(
        """(https://www\.|http://www\.|https://|http://)?[a-zA-Z]{2,}(\.[a-zA-Z]{2,})(\.[a-zA-Z]{2,})?/[a-zA-Z0-9]{2,}|((https://www\.|http://www\.|https://|http://)?[a-zA-Z]{2,}(\.[a-zA-Z]{2,})(\.[a-zA-Z]{2,})?)|(https://www\.|http://www\.|https://|http://)?[a-zA-Z0-9]{2,}\.[a-zA-Z0-9]{2,}\.[a-zA-Z0-9]{2,}(\.[a-zA-Z0-9]{2,})?"""
    )

    /**
     * Checks if the entire input text is strictly a URL (no surrounding text or sentence context).
     */
    fun isPureUrl(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.contains(Regex("\\s"))) return false
        val match = URL_REGEX.find(trimmed) ?: return false
        return match.value == trimmed || trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("www.")
    }

    /**
     * Replaces embedded URLs in text with "[Link]".
     */
    fun sanitizeUrls(text: String): String {
        return text.replace(URL_REGEX, "[Link]")
    }

    /**
     * Smart formatting:
     * - Strips invisible and zero-width characters (\uFEFF, \u200B, \u200C, \u200D).
     * - Converts non-breaking spaces (\u00A0) to standard spaces.
     * - Normalizes Windows (\r\n) and classic Mac (\r) line breaks to standard \n.
     * - Replaces embedded URLs with [Link].
     * - Collapses multiple consecutive spaces and tabs within each line.
     * - Trims leading and trailing spaces on each line.
     * - Collapses 3+ consecutive line breaks into a max of 2 breaks (\n\n) to eliminate large blank gaps.
     */
    fun smartFormat(text: String): String {
        val cleanChars = text
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\u00A0", " ")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val sanitized = sanitizeUrls(cleanChars)

        val formattedLines = sanitized.lines()
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }

        val joined = formattedLines.joinToString("\n")
        return joined.replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    /**
     * Counts words in text.
     * For alphabetic/spaced scripts (English, Vietnamese, European, etc.), words are delimited by whitespace.
     * For CJK logographic scripts (Chinese Hanzi, Japanese Kanji/Kana, Korean Hangul), each ideographic character
     * represents a morpheme/word unit and is counted individually.
     */
    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        val cjkRegex = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]")
        val cjkCharCount = cjkRegex.findAll(text).count()
        val nonCjkText = text.replace(cjkRegex, " ").trim()
        val nonCjkWordCount = if (nonCjkText.isBlank()) 0 else nonCjkText.split(Regex("\\s+")).count { it.isNotBlank() }
        return cjkCharCount + nonCjkWordCount
    }
}

