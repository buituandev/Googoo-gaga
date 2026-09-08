package com.example.data.subtitle

import java.util.regex.Pattern

object SubtitleParser {

    private val SRT_TIMING_REGEX = Pattern.compile(
        "^(\\d{1,2}:\\d{2}:\\d{2}[,\\.]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[,\\.]\\d{1,3})(.*)$"
    )

    private val VTT_TIMING_REGEX = Pattern.compile(
        "^((?:\\d{1,2}:)?\\d{2}:\\d{2}\\.\\d{1,3})\\s*-->\\s*((?:\\d{1,2}:)?\\d{2}:\\d{2}\\.\\d{1,3})(.*)$"
    )

    private val LRC_LINE_REGEX = Pattern.compile(
        "^\\[\\d{2}:\\d{2}[\\.:]\\d{2,3}\\].*"
    )

    /**
     * Detect format of the file or pasted text.
     */
    fun detectFormat(content: String, fileName: String? = null): InputFormat {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        when (ext) {
            "srt" -> return InputFormat.SUBTITLE_SRT
            "vtt" -> return InputFormat.SUBTITLE_VTT
            "json" -> return InputFormat.JSON
            "md", "markdown" -> return InputFormat.MARKDOWN
            "lrc" -> return InputFormat.LRC
        }

        val trimmed = content.trim()
        if (trimmed.startsWith("WEBVTT", ignoreCase = true) ||
            trimmed.contains("-->") && VTT_TIMING_REGEX.matcher(trimmed).find()
        ) {
            return InputFormat.SUBTITLE_VTT
        }

        // SRT check: contains number line followed by timestamp arrow
        val lines = trimmed.lines()
        for (i in 0 until (lines.size - 1).coerceAtMost(30)) {
            val timingMatcher = SRT_TIMING_REGEX.matcher(lines[i + 1].trim())
            if (lines[i].trim().toIntOrNull() != null && timingMatcher.matches()) {
                return InputFormat.SUBTITLE_SRT
            }
        }

        // Any generic timing arrow check
        if (lines.any { SRT_TIMING_REGEX.matcher(it.trim()).matches() }) {
            return InputFormat.SUBTITLE_SRT
        }

        // Check LRC
        val lrcMatches = lines.take(15).count { LRC_LINE_REGEX.matcher(it.trim()).matches() }
        if (lrcMatches >= 2) {
            return InputFormat.LRC
        }

        // Check JSON
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) {
            return InputFormat.JSON
        }

        // Check Markdown
        if (lines.any { it.startsWith("# ") || it.startsWith("## ") || it.startsWith("### ") || it.startsWith("```") }) {
            return InputFormat.MARKDOWN
        }

        return InputFormat.PLAIN_TEXT
    }

    /**
     * Parse SRT subtitle content into structured cues.
     */
    fun parseSrt(content: String): Result<List<SubtitleCue>> {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(Regex("\n{2,}"))
        val cues = mutableListOf<SubtitleCue>()
        var fallbackIndex = 1

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var timingLineIndex = -1
            var startTime = ""
            var endTime = ""
            var rawTiming = ""

            for (i in lines.indices) {
                val matcher = SRT_TIMING_REGEX.matcher(lines[i])
                if (matcher.matches()) {
                    timingLineIndex = i
                    startTime = matcher.group(1) ?: ""
                    endTime = matcher.group(2) ?: ""
                    rawTiming = lines[i]
                    break
                }
            }

            if (timingLineIndex != -1) {
                val cueIndex = if (timingLineIndex > 0) {
                    lines[timingLineIndex - 1].toIntOrNull() ?: fallbackIndex
                } else {
                    fallbackIndex
                }

                val textLines = lines.drop(timingLineIndex + 1)
                val text = textLines.joinToString("\n").trim()
                val isNoise = SubtitleNoiseFilter.isNoiseOrFiller(text)

                cues.add(
                    SubtitleCue(
                        id = cueIndex,
                        startTime = startTime,
                        endTime = endTime,
                        rawTimingLine = rawTiming,
                        text = text,
                        isNoise = isNoise
                    )
                )
                fallbackIndex = cueIndex + 1
            }
        }

        return if (cues.isNotEmpty()) {
            Result.success(cues)
        } else {
            Result.failure(IllegalArgumentException("Invalid SRT format. No valid subtitle cues detected."))
        }
    }

    /**
     * Parse WebVTT subtitle content into structured cues.
     */
    fun parseVtt(content: String): Result<List<SubtitleCue>> {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines().map { it.trim() }

        val cues = mutableListOf<SubtitleCue>()
        var index = 1
        var i = 0

        // Skip WEBVTT header and initial comments/notes
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("WEBVTT", ignoreCase = true) || line.startsWith("NOTE", ignoreCase = true)) {
                i++
                continue
            }
            if (line.isEmpty()) {
                i++
                continue
            }

            var timingLine = ""
            var startTime = ""
            var endTime = ""

            val matcher = VTT_TIMING_REGEX.matcher(line)
            if (matcher.matches()) {
                timingLine = line
                startTime = matcher.group(1) ?: ""
                endTime = matcher.group(2) ?: ""
                i++
            } else if (i + 1 < lines.size) {
                val nextMatcher = VTT_TIMING_REGEX.matcher(lines[i + 1])
                if (nextMatcher.matches()) {
                    timingLine = lines[i + 1]
                    startTime = nextMatcher.group(1) ?: ""
                    endTime = nextMatcher.group(2) ?: ""
                    i += 2
                }
            }

            if (timingLine.isNotEmpty()) {
                val textBuilder = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotEmpty() && !lines[i].startsWith("NOTE")) {
                    textBuilder.add(lines[i])
                    i++
                }
                val text = textBuilder.joinToString("\n").trim()
                val isNoise = SubtitleNoiseFilter.isNoiseOrFiller(text)

                cues.add(
                    SubtitleCue(
                        id = index++,
                        startTime = startTime,
                        endTime = endTime,
                        rawTimingLine = timingLine,
                        text = text,
                        isNoise = isNoise
                    )
                )
            } else {
                i++
            }
        }

        return if (cues.isNotEmpty()) {
            Result.success(cues)
        } else {
            Result.failure(IllegalArgumentException("Invalid WebVTT format. No valid subtitle cues detected."))
        }
    }

    /**
     * Parse subtitle automatically based on detected format.
     */
    fun parseSubtitle(content: String, format: InputFormat): Result<List<SubtitleCue>> {
        return when (format) {
            InputFormat.SUBTITLE_SRT -> parseSrt(content)
            InputFormat.SUBTITLE_VTT -> parseVtt(content)
            else -> {
                // Try SRT first, then VTT
                parseSrt(content).recoverCatching {
                    parseVtt(content).getOrThrow()
                }
            }
        }
    }

    /**
     * Assemble cues back into standard SRT format.
     */
    fun assembleSrt(cues: List<SubtitleCue>): String {
        return buildString {
            cues.forEachIndexed { i, cue ->
                append(i + 1)
                append("\n")
                append(cue.rawTimingLine)
                append("\n")
                append(cue.effectiveTranslation)
                if (i < cues.size - 1) {
                    append("\n\n")
                }
            }
        }
    }

    /**
     * Assemble cues back into standard WebVTT format.
     */
    fun assembleVtt(cues: List<SubtitleCue>): String {
        return buildString {
            append("WEBVTT\n\n")
            cues.forEachIndexed { i, cue ->
                append(cue.rawTimingLine)
                append("\n")
                append(cue.effectiveTranslation)
                if (i < cues.size - 1) {
                    append("\n\n")
                }
            }
        }
    }

    /**
     * Verify timestamp by timestamp and cue-by-cue matching between original and translated subtitle.
     */
    fun validateDoubleCheck(original: List<SubtitleCue>, translated: List<SubtitleCue>): Result<Unit> {
        if (original.size != translated.size) {
            return Result.failure(
                IllegalStateException("Double-check failed: Cue count mismatch. Original has ${original.size} cues, translated has ${translated.size} cues.")
            )
        }

        for (i in original.indices) {
            val orig = original[i]
            val trans = translated[i]
            if (orig.startTime != trans.startTime || orig.endTime != trans.endTime) {
                return Result.failure(
                    IllegalStateException(
                        "Double-check failed: Timestamp mismatch at cue ${i + 1}. Expected '${orig.startTime} --> ${orig.endTime}', but found '${trans.startTime} --> ${trans.endTime}'."
                    )
                )
            }
        }

        return Result.success(Unit)
    }
}
