package com.techfox

import com.techfox.data.subtitle.InputFormat
import com.techfox.data.subtitle.SubtitleCharacter
import com.techfox.data.subtitle.SubtitleCue
import com.techfox.data.subtitle.SubtitleJobEntity
import com.techfox.data.subtitle.SubtitleNoiseFilter
import com.techfox.data.subtitle.SubtitleParser
import com.techfox.data.subtitle.SubtitlePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubtitleFeatureTest {

    @Test
    fun testNoiseFilter_identifiesVocalSoundsLaughterAndWhimpering() {
        // Direct noise examples from user prompt
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("hmm"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("Hmm..."))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("a"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("aa"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("ah"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("ahh"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("haha"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("hahaha"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("Haha!"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("uhm"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("sigh"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("*whimpering*"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("[applause]"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("(Music playing)"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("♪"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("..."))
    }

    @Test
    fun testNoiseFilter_retainsActualDialogue() {
        assertFalse(SubtitleNoiseFilter.isNoiseOrFiller("Ah, let's go over there."))
        assertFalse(SubtitleNoiseFilter.isNoiseOrFiller("Haha, that is hilarious!"))
        assertFalse(SubtitleNoiseFilter.isNoiseOrFiller("Hmm, I am not sure about this plan."))
        assertFalse(SubtitleNoiseFilter.isNoiseOrFiller("What do you think?"))
    }

    @Test
    fun testNoiseFilter_doesNotFilterHtmlTaggedDialogue() {
        // User's exact subtitle snippet
        assertFalse(SubtitleNoiseFilter.isNoiseOrFiller("For www.forom.com"))
        assertFalse(SubtitleNoiseFilter.isNoiseOrFiller("<i>Tonight's the night.</i>"))
        assertFalse(SubtitleNoiseFilter.isNoiseOrFiller("<i>And it's going to happen\nagain and again --</i>"))

        // Other HTML-tagged dialogue
        assertFalse(SubtitleNoiseFilter.isNoiseOrFiller("<b>Warning: emergency exit</b>"))
        assertFalse(SubtitleNoiseFilter.isNoiseOrFiller("<font color=\"#ffff00\">I see them!</font>"))

        // HTML-tagged noise SHOULD still be filtered
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("<i>hmm...</i>"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("<i>[Music playing]</i>"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("<i>haha</i>"))
        assertTrue(SubtitleNoiseFilter.isNoiseOrFiller("<b>(crying)</b>"))
    }

    @Test
    fun testFormatDetection() {
        // Extension based
        assertEquals(InputFormat.SUBTITLE_SRT, SubtitleParser.detectFormat("", "movie.srt"))
        assertEquals(InputFormat.SUBTITLE_VTT, SubtitleParser.detectFormat("", "episode.vtt"))
        assertEquals(InputFormat.JSON, SubtitleParser.detectFormat("", "data.json"))
        assertEquals(InputFormat.MARKDOWN, SubtitleParser.detectFormat("", "notes.md"))
        assertEquals(InputFormat.LRC, SubtitleParser.detectFormat("", "song.lrc"))

        // Content based
        val srtSample = """
            1
            00:00:01,000 --> 00:00:04,000
            Hello there!
            
            2
            00:00:05,000 --> 00:00:08,000
            General Kenobi!
        """.trimIndent()
        assertEquals(InputFormat.SUBTITLE_SRT, SubtitleParser.detectFormat(srtSample))

        val vttSample = """
            WEBVTT
            
            00:00:01.000 --> 00:00:04.000
            Welcome to the video.
        """.trimIndent()
        assertEquals(InputFormat.SUBTITLE_VTT, SubtitleParser.detectFormat(vttSample))

        val jsonSample = "{\n  \"message\": \"hello world\"\n}"
        assertEquals(InputFormat.JSON, SubtitleParser.detectFormat(jsonSample))

        val mdSample = "# Title\n\nThis is a markdown doc."
        assertEquals(InputFormat.MARKDOWN, SubtitleParser.detectFormat(mdSample))

        val lrcSample = "[00:12.00]First line of song\n[00:15.50]Second line of song"
        assertEquals(InputFormat.LRC, SubtitleParser.detectFormat(lrcSample))
    }

    @Test
    fun testParseSrt_parsesAndIdentifiesNoiseLines() {
        val srt = """
            1
            00:01:00,000 --> 00:01:03,000
            Hello detective.
            
            2
            00:01:04,000 --> 00:01:05,000
            hmm...
            
            3
            00:01:06,000 --> 00:01:09,000
            Where were you last night?
            
            4
            00:01:10,000 --> 00:01:11,500
            [laughter]
        """.trimIndent()

        val parseResult = SubtitleParser.parseSrt(srt)
        assertTrue(parseResult.isSuccess)
        val cues = parseResult.getOrThrow()
        assertEquals(4, cues.size)

        assertFalse(cues[0].isNoise)
        assertEquals("Hello detective.", cues[0].text)

        assertTrue(cues[1].isNoise)
        assertEquals("hmm...", cues[1].text)

        assertFalse(cues[2].isNoise)
        assertEquals("Where were you last night?", cues[2].text)

        assertTrue(cues[3].isNoise)
        assertEquals("[laughter]", cues[3].text)
    }

    @Test
    fun testParseVtt_parsesCorrectly() {
        val vtt = """
            WEBVTT
            
            00:01.000 --> 00:04.000
            Line one
            
            00:05.000 --> 00:07.000
            Line two
        """.trimIndent()

        val parseResult = SubtitleParser.parseVtt(vtt)
        assertTrue(parseResult.isSuccess)
        val cues = parseResult.getOrThrow()
        assertEquals(2, cues.size)
        assertEquals("Line one", cues[0].text)
        assertEquals("Line two", cues[1].text)
    }

    @Test
    fun testInvalidSubtitleRejection() {
        val invalidText = "This is just regular text with no subtitle timings at all."
        val result = SubtitleParser.parseSrt(invalidText)
        assertTrue(result.isFailure)
    }

    @Test
    fun testDoubleCheckValidation_detectsTimestampMismatch() {
        val original = listOf(
            SubtitleCue(1, "00:00:01,000", "00:00:04,000", "00:00:01,000 --> 00:00:04,000", "Hello"),
            SubtitleCue(2, "00:00:05,000", "00:00:08,000", "00:00:05,000 --> 00:00:08,000", "World")
        )

        // Perfect match
        val validTranslated = listOf(
            SubtitleCue(1, "00:00:01,000", "00:00:04,000", "00:00:01,000 --> 00:00:04,000", "Hello", translatedText = "Xin chào"),
            SubtitleCue(2, "00:00:05,000", "00:00:08,000", "00:00:05,000 --> 00:00:08,000", "World", translatedText = "Thế giới")
        )
        assertTrue(SubtitleParser.validateDoubleCheck(original, validTranslated).isSuccess)

        // Timestamp mismatch
        val corruptedTranslated = listOf(
            SubtitleCue(1, "00:00:01,000", "00:00:04,000", "00:00:01,000 --> 00:00:04,000", "Hello", translatedText = "Xin chào"),
            SubtitleCue(2, "00:00:06,000", "00:00:09,000", "00:00:06,000 --> 00:00:09,000", "World", translatedText = "Thế giới")
        )
        assertTrue(SubtitleParser.validateDoubleCheck(original, corruptedTranslated).isFailure)

        // Count mismatch
        val missingTranslated = listOf(
            SubtitleCue(1, "00:00:01,000", "00:00:04,000", "00:00:01,000 --> 00:00:04,000", "Hello", translatedText = "Xin chào")
        )
        assertTrue(SubtitleParser.validateDoubleCheck(original, missingTranslated).isFailure)
    }

    @Test
    fun testCharacterLimit_maxFifteenPersons() {
        assertEquals(15, SubtitleCharacter.MAX_CHARACTERS_COUNT)
        val charList = (1..20).map {
            SubtitleCharacter(name = "Person $it", description = "Role description for person $it")
        }
        val cappedList = charList.take(SubtitleCharacter.MAX_CHARACTERS_COUNT)
        assertEquals(15, cappedList.size)
        // Descriptions are not truncated to 15 characters
        assertEquals("Role description for person 1", cappedList[0].description)
    }

    @Test
    fun testSmartPickupSerialization() {
        val cues = listOf(
            SubtitleCue(1, "00:00:01,000", "00:00:03,000", "00:00:01,000 --> 00:00:03,000", "First line", false, "Dòng một"),
            SubtitleCue(2, "00:00:04,000", "00:00:05,000", "00:00:04,000 --> 00:00:05,000", "hmm", true, "hmm")
        )

        val json = SubtitleJobEntity.serializeCues(cues)
        val deserialized = SubtitleJobEntity.deserializeCues(json)

        assertEquals(2, deserialized.size)
        assertEquals(cues[0].id, deserialized[0].id)
        assertEquals(cues[0].startTime, deserialized[0].startTime)
        assertEquals("Dòng một", deserialized[0].translatedText)
        assertTrue(deserialized[1].isNoise)
    }

    @Test
    fun testSubtitlePreset_defaultsToCinematic() {
        assertEquals("cinematic", SubtitlePreset.DEFAULT_PRESET.id)
        assertEquals("Cinematic Standard", SubtitlePreset.DEFAULT_PRESET.defaultName)
        assertEquals(SubtitlePreset.CINEMATIC, SubtitlePreset.getById("cinematic"))
        assertEquals(SubtitlePreset.CINEMATIC, SubtitlePreset.getById(null))
        assertEquals(SubtitlePreset.CINEMATIC, SubtitlePreset.getById("non_existent_preset"))
    }

    @Test
    fun testSubtitlePreset_allPresetsConfigured() {
        assertTrue(SubtitlePreset.ALL_PRESETS.size >= 7)
        assertTrue(SubtitlePreset.ALL_PRESETS.any { it.id == SubtitlePreset.ID_CINEMATIC })
        assertTrue(SubtitlePreset.ALL_PRESETS.any { it.id == SubtitlePreset.ID_THRILLER })
        assertTrue(SubtitlePreset.ALL_PRESETS.any { it.id == SubtitlePreset.ID_COMEDY })
        assertTrue(SubtitlePreset.ALL_PRESETS.any { it.id == SubtitlePreset.ID_ACTION })
        assertTrue(SubtitlePreset.ALL_PRESETS.any { it.id == SubtitlePreset.ID_ANIME })
        assertTrue(SubtitlePreset.ALL_PRESETS.any { it.id == SubtitlePreset.ID_DOCUMENTARY })
        assertTrue(SubtitlePreset.ALL_PRESETS.any { it.id == SubtitlePreset.ID_CUSTOM })

        SubtitlePreset.ALL_PRESETS.forEach { preset ->
            assertTrue(preset.id.isNotBlank())
            assertTrue(preset.defaultName.isNotBlank())
            assertTrue(preset.defaultDescription.isNotBlank())
            assertTrue(preset.systemPromptDirectives.isNotBlank())
            assertTrue(preset.iconEmoji.isNotBlank())
        }
    }

    @Test
    fun testSubtitlePreset_cinematicPromptBuild() {
        val cues = listOf(
            SubtitleCue(1, "00:00:01,000", "00:00:03,000", "00:00:01,000 --> 00:00:03,000", "Tonight's the night.")
        )
        val prompt = SubtitlePreset.CINEMATIC.buildSubtitlePrompt(
            targetLanguage = "Vietnamese",
            contextDescription = "Miami dark night",
            customInstruction = "",
            characters = listOf(SubtitleCharacter(name = "Dexter", description = "Blood spatter analyst")),
            rollingContext = emptyList(),
            cuesToTranslate = cues
        )

        assertTrue(prompt.contains("Vietnamese"))
        assertTrue(prompt.contains("Strictly NO Word-for-Word Literal Translation"))
        assertTrue(prompt.contains("Miami dark night"))
        assertTrue(prompt.contains("Dexter: Blood spatter analyst"))
        assertTrue(prompt.contains("Tonight's the night."))
    }

    @Test
    fun testSubtitlePreset_customPromptBuild() {
        val cues = listOf(
            SubtitleCue(1, "00:00:01,000", "00:00:03,000", "00:00:01,000 --> 00:00:03,000", "Greetings, traveller.")
        )
        val customDirectives = "You are a high fantasy RPG translator. Use archaic medieval vocabulary."
        val prompt = SubtitlePreset.CUSTOM.buildSubtitlePrompt(
            targetLanguage = "Vietnamese",
            contextDescription = "",
            customInstruction = customDirectives,
            characters = emptyList(),
            rollingContext = emptyList(),
            cuesToTranslate = cues
        )

        assertTrue(prompt.contains("You are a high fantasy RPG translator"))
        assertTrue(prompt.contains("Greetings, traveller."))
    }
}
