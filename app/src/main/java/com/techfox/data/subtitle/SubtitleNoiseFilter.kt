package com.techfox.data.subtitle

import java.util.regex.Pattern

object SubtitleNoiseFilter {

    // Common non-lexical vocal sounds, fillers, laughter, and whimpering
    private val NOISE_SOUNDS = setOf(
        "a", "aa", "aaa", "aaaa",
        "ah", "ahh", "ahhh", "aah", "aaah",
        "eh", "ehh", "er", "err", "erm",
        "ha", "haha", "hahaha", "hahahaha",
        "he", "hehe", "hehehe", "ho", "hoho",
        "hm", "hmm", "hmmm", "hmmmm",
        "huh", "huhh",
        "m", "mm", "mmm", "mmmm",
        "o", "oh", "ohh", "ohhh", "ooh", "oooh",
        "sh", "shh", "shhh", "shhhh",
        "tsk", "tskk",
        "uh", "uhh", "uhm", "um", "umm", "ummm",
        "ugh", "ughh",
        "wow", "woah", "whoa",
        "yay", "yeah", "yep", "nah",
        "sigh", "sighs", "gasp", "gasps", "sob", "sobs",
        "groan", "groans", "whimper", "whimpers", "whimpering", "yawn", "yawns",
        "snicker", "snickers", "sniffle", "sniffles"
    )

    // Stems that indicate sound effects or audio descriptions inside brackets
    private val SOUND_EFFECT_STEMS = listOf(
        "music", "applause", "cheer", "laugh", "cry", "sob", "sigh",
        "gasp", "groan", "whimper", "snicker", "sniffle", "cough",
        "scream", "chuckle", "pant", "giggle", "grunt", "yawn", "moan",
        "inaudible", "ambient", "instrumental", "silence", "chime",
        "shriek", "whisper", "snort", "throat"
    )

    // Bracketed sound patterns: [ ... ], ( ... ), * ... *, ♪ ... ♪, ♫ ... ♫
    // Note: NEVER include < or > here because HTML formatting tags (<i>, <b>, <font>) use angle brackets!
    private val BRACKETED_REGEX = Pattern.compile(
        "^(\\[[^\\]]+\\]|\\([^\\)]+\\)|\\*[^\\*]+\\*|[♪♫].*[♪♫])$",
        Pattern.DOTALL
    )

    // Repeating syllable check like "ha-ha", "he-he", "ah-ah"
    private val REPEATING_SYLLABLE_REGEX = Pattern.compile(
        "^(ha|he|ho|ah|eh|oh|hm|mm|la)+$",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Determines whether the given subtitle cue text is purely noise, filler, vocalization,
     * or sound effect with no translatable semantic value.
     *
     * If true, this cue will not be sent to AI (saving tokens), but kept in the final subtitle file.
     */
    fun isNoiseOrFiller(text: String): Boolean {
        // 1. Strip all HTML/formatting tags first (e.g. <i>, </i>, <b>, </b>, <font ...>, </font>)
        // so formatted dialogue like "<i>Tonight's the night.</i>" is never mistaken for bracketed noise!
        val stripped = text.replace(Regex("<[^>]+>"), "").trim()
        if (stripped.isEmpty()) return true

        // 2. Pure musical notes, e.g. ♪ or ♫
        if (stripped.all { it == '♪' || it == '♫' || it.isWhitespace() }) {
            return true
        }

        // 3. Bracketed sound effects: [Music], (Laughter), *crying*, ♪ Jazz playing ♪
        if (BRACKETED_REGEX.matcher(stripped).matches()) {
            val inner = stripped
                .removeSurrounding("[", "]")
                .removeSurrounding("(", ")")
                .removeSurrounding("*", "*")
                .removeSurrounding("♪", "♪")
                .removeSurrounding("♫", "♫")
                .trim()
                .lowercase()

            // Check if inner content contains known sound effect keywords
            val innerWords = inner
                .replace(Regex("[\\p{Punct}\\p{Digit}♪♫~]+"), " ")
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            if (innerWords.isEmpty()) return true

            val hasSoundEffectKeyword = innerWords.any { word ->
                SOUND_EFFECT_STEMS.any { stem -> word.startsWith(stem) }
            }
            val allAreNoise = innerWords.all { NOISE_SOUNDS.contains(it) || isElongatedNoise(it) }

            if (hasSoundEffectKeyword || allAreNoise || stripped.contains('♪') || stripped.contains('♫')) {
                return true
            }

            // If it's bracketed but contains real dialogue (e.g. "[Tonight's the night.]"), do NOT filter it
            return false
        }

        // 4. Remove all punctuation and symbols to test core vocalization
        // e.g. "Hmm...", "Haha!", "Ah?!", "--"
        val cleanWords = stripped
            .replace(Regex("[\\p{Punct}\\p{Digit}♪♫~]+"), " ")
            .trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        // If after stripping punctuation there are no words left (e.g. "...", "???", "- -")
        if (cleanWords.isEmpty()) {
            return true
        }

        // 5. If every single token in the line is a known vocal sound (hmm, haha, a, aa, ah, etc.)
        val allTokensAreNoise = cleanWords.all { word ->
            NOISE_SOUNDS.contains(word) ||
                    REPEATING_SYLLABLE_REGEX.matcher(word).matches() ||
                    isElongatedNoise(word)
        }

        return allTokensAreNoise
    }

    /**
     * Checks for elongated vocalizations like "hmmmmmmm", "aaaaaah", "haaaaa".
     */
    private fun isElongatedNoise(word: String): Boolean {
        if (word.length <= 1) return true
        val collapsed = word.replace(Regex("(.)\\1+"), "$1")
        return NOISE_SOUNDS.contains(collapsed) ||
                REPEATING_SYLLABLE_REGEX.matcher(collapsed).matches()
    }
}
