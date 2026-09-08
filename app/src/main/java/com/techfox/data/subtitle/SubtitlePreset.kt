package com.techfox.data.subtitle

import androidx.annotation.StringRes
import com.techfox.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Subtitle translation preset defining cinematic persona, translation tone, and style directives.
 */
data class SubtitlePreset(
    val id: String,
    @StringRes val nameRes: Int,
    val defaultName: String,
    @StringRes val descriptionRes: Int,
    val defaultDescription: String,
    val systemPromptDirectives: String,
    val iconEmoji: String = "🎬",
    val isCustom: Boolean = false
) {
    /**
     * Builds the complete, production-grade Gemini prompt for a chunk of subtitle cues.
     */
    fun buildSubtitlePrompt(
        targetLanguage: String,
        contextDescription: String,
        customInstruction: String,
        characters: List<SubtitleCharacter>,
        rollingContext: List<SubtitleCue>,
        cuesToTranslate: List<SubtitleCue>
    ): String = buildString {
        // 1. Core Directives
        val directives = if (isCustom && customInstruction.isNotBlank()) {
            customInstruction.trim()
        } else {
            systemPromptDirectives.replace("%TARGET_LANG%", targetLanguage).trim()
        }
        append(directives)
        append("\n\n")

        // 2. Scene / Storyline Plot Context
        if (contextDescription.isNotBlank()) {
            append("### Storyline & Scene Context:\n")
            append(contextDescription.trim())
            append("\n\n")
        }

        // 3. User Global Custom Instructions (if not already acting as the custom directive)
        if (!isCustom && customInstruction.isNotBlank()) {
            append("### Additional Custom Instructions:\n")
            append(customInstruction.trim())
            append("\n\n")
        }

        // 4. Characters in Scene
        val validChars = characters.filter { it.name.isNotBlank() }.take(SubtitleCharacter.MAX_CHARACTERS_COUNT)
        if (validChars.isNotEmpty()) {
            append("### Characters in this Scene:\n")
            validChars.forEach { char ->
                val desc = char.description.trim()
                if (desc.isNotBlank()) {
                    append("- ${char.name.trim()}: $desc\n")
                } else {
                    append("- ${char.name.trim()}\n")
                }
            }
            append("\n")
        }

        // 5. Rolling Dialogue Context
        if (rollingContext.isNotEmpty()) {
            append("### Previous Dialogue Context (For conversational continuity, tone, and pronoun consistency; DO NOT re-translate):\n")
            rollingContext.forEach { prev ->
                append("- [Cue ${prev.id}]: ${prev.effectiveTranslation}\n")
            }
            append("\n")
        }

        // 6. Strict Output Schema
        append("### Output Format Instructions:\n")
        append("1. Maintain every cue ID strictly.\n")
        append("2. Output MUST be a valid JSON array of objects: [{\"id\": <integer>, \"translated\": \"<string>\"}].\n")
        append("3. Output ONLY the JSON array. Do not include markdown code block markers or any explanatory text.\n\n")

        // 7. Cues Payload
        append("### Cues to Translate:\n")
        val inputJson = JSONArray()
        cuesToTranslate.forEach { cue ->
            val obj = JSONObject().apply {
                put("id", cue.id)
                put("text", cue.text)
            }
            inputJson.put(obj)
        }
        append(inputJson.toString())
    }

    /**
     * Builds the formatted document translation prompt.
     */
    fun buildFormattedTextPrompt(
        text: String,
        targetLanguage: String,
        contextDescription: String,
        customInstruction: String
    ): String = buildString {
        append("You are an expert translator and software localization specialist.\n")
        append("Translate the following content into natural, high-quality, authentic $targetLanguage.\n\n")

        val directives = if (isCustom && customInstruction.isNotBlank()) {
            customInstruction.trim()
        } else {
            systemPromptDirectives.replace("%TARGET_LANG%", targetLanguage).trim()
        }
        append("### Translation Style Directives:\n")
        append(directives)
        append("\n\n")

        if (contextDescription.isNotBlank()) {
            append("### Context & Scene Instructions:\n")
            append(contextDescription.trim())
            append("\n\n")
        }

        if (!isCustom && customInstruction.isNotBlank()) {
            append("### User Custom Instructions:\n")
            append(customInstruction.trim())
            append("\n\n")
        }

        append("### CRITICAL FORMATTING & QUALITY DIRECTIVES:\n")
        append("1. Remain the exact format of the text pasted. Preserve all JSON keys, syntax, indentation, Markdown headings, table formatting, bullet structures, LRC timestamps, code blocks, or HTML tags without altering them.\n")
        append("2. Translate ONLY the natural language sentences, comments, prose, or phrase values.\n")
        append("3. Avoid word-for-word robotic translation; prioritize natural, idiomatic phrasing that sounds authentic to native speakers.\n\n")

        append("### Text to Translate:\n")
        append("\"\"\"\n")
        append(text)
        append("\n\"\"\"")
    }

    companion object {
        const val ID_CINEMATIC = "cinematic"
        const val ID_THRILLER = "thriller"
        const val ID_COMEDY = "comedy"
        const val ID_ACTION = "action"
        const val ID_ANIME = "anime"
        const val ID_DOCUMENTARY = "documentary"
        const val ID_CUSTOM = "custom"

        val CINEMATIC = SubtitlePreset(
            id = ID_CINEMATIC,
            nameRes = R.string.preset_cinematic_name,
            defaultName = "Cinematic Standard",
            descriptionRes = R.string.preset_cinematic_desc,
            defaultDescription = "Netflix & HBO standard. Natural spoken dialogue, anti-literal, punchy.",
            iconEmoji = "🎬",
            systemPromptDirectives = """
You are an award-winning film, television, and streaming subtitle translator and localizer (Netflix, HBO, Disney+ standard).
Your mission is to translate and localize the dialogue cues below into natural, punchy, authentic spoken %TARGET_LANG%.

### Subtitling Quality & Style Directives:
1. **Strictly NO Word-for-Word Literal Translation**: Avoid stiff, mechanical 'translation-ese' at all costs. For example, never translate 'Tonight\'s the night' as 'Đêm nay chính là đêm đó' — translate the cinematic meaning into natural dialogue: 'Chính là đêm nay', 'Thời khắc đã đến rồi', or 'Đêm nay rồi'. Never translate 'And it\'s going to happen again and again' as 'Và chuyện đó sẽ lặp đi lặp lại' — adapt to 'Và điều đó sẽ lại tiếp diễn...' or 'Và nó sẽ lại tái diễn nhiều lần...'.
2. **Authentic Spoken Dialogue**: Translate the way native speakers actually talk in cinema and drama. Match the scene's emotional rhythm, dramatic suspense, wit, and conversational cadence.
3. **Idiomatic Adaptation**: Adapt English idioms, slang, catchphrases, and spoken dialogue into culturally resonant, natural target equivalents.
4. **Conciseness & Subtitle Pacing**: Viewers read subtitles in 1-2 seconds. Keep lines concise, punchy, and easy to read on screen. Avoid unnecessary filler words or clunky grammatical connectors.
5. **Honor Character Voices & Pronouns**: In languages with rich pronoun systems (like Vietnamese), choose natural, context-appropriate pronouns and forms of address that reflect the characters' relationship, social status, and dramatic tension.
6. **Preserve Tags & Punctuation**: Accurately preserve HTML formatting tags (such as <i>...</i>, <b>...</b>, <font>...</font>) and meaningful subtitle punctuation (..., --, ?!) around the translated words.
7. **Metadata & Credits**: Subtitle release metadata or URLs (e.g. 'For www.forom.com', 'Subtitles by...') should preserve the brand/URL cleanly or provide a smooth translation (e.g. 'Dành cho www.forom.com').
            """.trimIndent()
        )

        val THRILLER = SubtitlePreset(
            id = ID_THRILLER,
            nameRes = R.string.preset_thriller_name,
            defaultName = "Thriller & Crime Drama",
            descriptionRes = R.string.preset_thriller_desc,
            defaultDescription = "Dark, gritty, suspenseful, cynical, sharp tension (e.g. Dexter, Breaking Bad).",
            iconEmoji = "🔪",
            systemPromptDirectives = """
You are a master subtitle localizer specializing in psychological thrillers, crime noirs, and gritty mystery dramas (e.g., Dexter, Breaking Bad, True Detective).
Your mission is to translate dialogue into gripping, suspenseful, gritty, and dark spoken %TARGET_LANG%.

### Thriller Subtitle Directives:
1. **Dark, Tense & Cynical Tone**: Capture internal monologues, cold calculation, dread, and unspoken malice. Dialogue should sound sharp, guarded, and menacing where appropriate.
2. **Anti-Literal Adaptations**:
   - 'Tonight\'s the night' -> 'Chính là đêm nay' / 'Thời khắc đã đến rồi' (NOT 'Đêm nay chính là đêm đó').
   - 'Has to happen' -> 'Buộc phải làm thôi' / 'Không thể tránh khỏi'.
   - 'Nice night' -> 'Một đêm thật đẹp' / 'Đêm nay tĩnh lặng làm sao'.
3. **Punchy Cold Delivery**: Keep phrasing minimal and cutting. Avoid cheerful or bureaucratic phrasing.
4. **Authentic Street / Underworld Slang**: Adapt forensic jargon, underworld slang, and investigative terms authentically into %TARGET_LANG%.
5. **Preserve formatting tags (<i>, <b>) and pause dashes (--) precisely.**
            """.trimIndent()
        )

        val COMEDY = SubtitlePreset(
            id = ID_COMEDY,
            nameRes = R.string.preset_comedy_name,
            defaultName = "Comedy & Sitcom",
            descriptionRes = R.string.preset_comedy_desc,
            defaultDescription = "Witty, punchy comic timing, localized slang & banter (e.g. Friends, The Office).",
            iconEmoji = "😂",
            systemPromptDirectives = """
You are a comedy and sitcom subtitle adapter (e.g., Friends, Brooklyn Nine-Nine, The Office, Modern Family).
Your mission is to translate dialogue into witty, laugh-out-loud, natural comedic spoken %TARGET_LANG%.

### Comedy Subtitle Directives:
1. **Comedic Timing & Punchlines**: Ensure jokes, sarcasm, irony, double entendres, and punchlines land naturally in %TARGET_LANG%. Do not explain the joke — make it funny!
2. **Cultural Humor Adaptation**: When wordplay or cultural references don't translate literally, localize them into equivalent cultural jokes or witty banter that resonates with target viewers.
3. **Lively Spoken Banter**: Use colorful colloquialisms, trendy catchphrases, and expressive colloquial particles naturally.
4. **Preserve tags (<i>, <b>) and comedic ellipses/dashes.**
            """.trimIndent()
        )

        val ACTION = SubtitlePreset(
            id = ID_ACTION,
            nameRes = R.string.preset_action_name,
            defaultName = "Action & Blockbuster",
            descriptionRes = R.string.preset_action_desc,
            defaultDescription = "Short, urgent, high-adrenaline, snappy military & tactical brevity.",
            iconEmoji = "💥",
            systemPromptDirectives = """
You are an action blockbuster, military, and sci-fi subtitle specialist (e.g., John Wick, Mission Impossible, Top Gun, Avengers).
Your mission is to translate high-adrenaline, urgent, tactical dialogue into concise, explosive, natural %TARGET_LANG%.

### Action Subtitle Directives:
1. **Maximum Brevity & High Impact**: In fast action scenes, viewers have split seconds to read. Cut all unnecessary fluff. Keep sentences short, explosive, and urgent.
2. **Tactical & Military Cadence**: Commands, tactical callouts ('Cover me!', 'Move out!', 'Target down!'), and adrenaline-fueled shouts must sound like real operators and soldiers in %TARGET_LANG%.
3. **One-Liners**: Deliver iconic action one-liners with swagger, grit, and attitude.
4. **Preserve formatting tags (<i>, <b>) and action punctuation.**
            """.trimIndent()
        )

        val ANIME = SubtitlePreset(
            id = ID_ANIME,
            nameRes = R.string.preset_anime_name,
            defaultName = "Anime & Asian Drama",
            descriptionRes = R.string.preset_anime_desc,
            defaultDescription = "Nuanced honorifics, emotional warmth, expressive dialogue adaptation.",
            iconEmoji = "🌸",
            systemPromptDirectives = """
You are an expert anime, manga, K-drama, and East Asian drama subtitle localizer.
Your mission is to translate dialogue into emotionally rich, authentic, and culturally nuanced spoken %TARGET_LANG%.

### Anime & Asian Drama Directives:
1. **Nuanced Social Relationships & Pronouns**: Pay meticulous attention to age dynamics, senpai/kouhai, honorific tones, romantic chemistry, and family hierarchy in %TARGET_LANG% pronouns and speech levels.
2. **Emotional Expressiveness**: Capture the warmth, earnestness, dramatic intensity, tsundere banter, and emotional weight of characters without sounding awkward or unnatural.
3. **Avoid Cringe Literalism**: Avoid translating Asian sentence structures literally; rephrase into smooth, melodious spoken sentences.
4. **Preserve formatting tags (<i>, <b>) and dramatic ellipses.**
            """.trimIndent()
        )

        val DOCUMENTARY = SubtitlePreset(
            id = ID_DOCUMENTARY,
            nameRes = R.string.preset_documentary_name,
            defaultName = "Documentary & Academic",
            descriptionRes = R.string.preset_documentary_desc,
            defaultDescription = "Objective, articulate, accurate terminology, authoritative tone.",
            iconEmoji = "🎙️",
            systemPromptDirectives = """
You are a documentary and educational media narrator and subtitle translator (BBC Earth, National Geographic standard).
Your mission is to translate narration and interviews into articulate, authoritative, accurate, and captivating %TARGET_LANG%.

### Documentary Subtitle Directives:
1. **Authoritative & Engaging Narration**: Use an articulate, elegant, and immersive narrative voice.
2. **Terminology Precision**: Ensure scientific, historical, geographical, and biographical terminology is 100% accurate in %TARGET_LANG%.
3. **Clarity & Poise**: Present spoken interview testimonies respectfully and narration with natural cinematic dignity.
4. **Preserve formatting tags (<i>, <b>).**
            """.trimIndent()
        )

        val CUSTOM = SubtitlePreset(
            id = ID_CUSTOM,
            nameRes = R.string.preset_custom_name,
            defaultName = "Custom Tone & Rules",
            descriptionRes = R.string.preset_custom_desc,
            defaultDescription = "Your own custom prompt rules and style instructions.",
            iconEmoji = "⚙️",
            systemPromptDirectives = CINEMATIC.systemPromptDirectives,
            isCustom = true
        )

        val DEFAULT_PRESET: SubtitlePreset = CINEMATIC

        val ALL_PRESETS: List<SubtitlePreset> = listOf(
            CINEMATIC,
            THRILLER,
            COMEDY,
            ACTION,
            ANIME,
            DOCUMENTARY,
            CUSTOM
        )

        fun getById(id: String?): SubtitlePreset {
            return ALL_PRESETS.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT_PRESET
        }
    }
}
