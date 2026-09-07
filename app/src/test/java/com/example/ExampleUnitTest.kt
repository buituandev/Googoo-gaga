package com.example

import com.example.data.GeminiTranslatorService
import com.example.data.KeyTermInsight
import com.example.data.TextSanitizer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for TextSanitizer and Gemini translation parsing & conditions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
    @Test
    fun testIsPureUrl_withValidUrls() {
        assertTrue(TextSanitizer.isPureUrl("https://google.com"))
        assertTrue(TextSanitizer.isPureUrl("http://example.com/path?arg=1&test=2#anchor"))
        assertTrue(TextSanitizer.isPureUrl("www.github.com/repository"))
        assertTrue(TextSanitizer.isPureUrl("  https://kotlinlang.org  "))
    }

    @Test
    fun testIsPureUrl_withSentencesAndNormalText() {
        assertFalse(TextSanitizer.isPureUrl("Check this link: https://google.com"))
        assertFalse(TextSanitizer.isPureUrl("https://google.com is a search engine"))
        assertFalse(TextSanitizer.isPureUrl("Hello world, how are you?"))
        assertFalse(TextSanitizer.isPureUrl(""))
        assertFalse(TextSanitizer.isPureUrl("   "))
    }

    @Test
    fun testSanitizeUrls_replacesEmbeddedUrlsWithLink() {
        val input = "Visit https://android.com for Android docs and www.google.com for search."
        val sanitized = TextSanitizer.sanitizeUrls(input)
        assertEquals("Visit [Link] for Android docs and [Link] for search.", sanitized)
    }

    @Test
    fun testSmartFormat_cleansInvisibleCharsMultipleSpacesAndExcessiveNewlines() {
        val raw = "Hello\uFEFF   world!\u200B  \n\n\n\n\nVisit https://example.com/test \t now.\n\n\nEnd."
        val formatted = TextSanitizer.smartFormat(raw)
        val expected = "Hello world!\n\nVisit [Link] now.\n\nEnd."
        assertEquals(expected, formatted)
    }

    @Test
    fun testCountWords() {
        assertEquals(0, TextSanitizer.countWords(""))
        assertEquals(0, TextSanitizer.countWords("   \n\t  "))
        assertEquals(2, TextSanitizer.countWords("Hello world"))
        assertEquals(5, TextSanitizer.countWords("This is   a test sentence."))
        assertEquals(15, TextSanitizer.countWords("One two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen"))
        assertEquals(16, TextSanitizer.countWords("One two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen"))
        // Chinese & CJK tests
        assertEquals(4, TextSanitizer.countWords("你好世界"))
        assertEquals(21, TextSanitizer.countWords("人工智能是一门极富挑战性的科学它的研究领域"))
        assertEquals(6, TextSanitizer.countWords("Hello world 你好世界"))
    }

    @Test
    fun testParseTranslation_shortTextUnder16Words_hasNoInsight() {
        val raw = """
            Direct Translation: Bonjour le monde
            Cultural Context: Some insight that should be suppressed.
        """.trimIndent()
        val output = GeminiTranslatorService.parseTranslation(
            rawText = raw,
            sourceText = "Hello world",
            targetLanguage = "French",
            isShortText = true
        )
        assertEquals("Bonjour le monde", output.directTranslation)
        assertEquals("", output.culturalContext)
    }

    @Test
    fun testParseTranslation_longText16WordsOrMore_hasInsight() {
        val raw = """
            **Direct Translation:** Bonjour le monde
            **Cultural Context:** Common friendly French greeting.
        """.trimIndent()
        val output = GeminiTranslatorService.parseTranslation(
            rawText = raw,
            sourceText = "Hello world this is a longer sentence intended to verify that cultural context is preserved when input exceeds length threshold.",
            targetLanguage = "French",
            isShortText = false
        )
        assertEquals("Bonjour le monde", output.directTranslation)
        assertEquals("Common friendly French greeting.", output.culturalContext)
    }

    @Test
    fun testParseTranslation_withMarkdownHeaders() {
        val raw = """
            **Direct Translation:** Bonjour le monde
            **Cultural Context:** Common friendly French greeting.
        """.trimIndent()
        val output = GeminiTranslatorService.parseTranslation(raw, "Hello world", "French")
        assertEquals("Bonjour le monde", output.directTranslation)
        assertEquals("Common friendly French greeting.", output.culturalContext)
    }

    @Test
    fun testParseTranslation_stripsBoldFromDirectTranslation() {
        val raw = """
            Direct Translation: **Xin chào thế giới**
            Cultural Context: Standard polite Vietnamese greeting.
        """.trimIndent()
        val output = GeminiTranslatorService.parseTranslation(raw, "Hello world", "Vietnamese")
        assertEquals("Xin chào thế giới", output.directTranslation)
        assertEquals("Standard polite Vietnamese greeting.", output.culturalContext)
    }

    @Test
    fun testParseTranslation_withInsightHeaderInTargetLanguage() {
        val raw = """
            **Direct Translation:** Cảm ơn bạn rất nhiều
            **Insight:** Cụm từ lịch sự và trang trọng được sử dụng phổ biến trong giao tiếp hàng ngày.
        """.trimIndent()
        val output = GeminiTranslatorService.parseTranslation(raw, "Thank you very much", "Vietnamese")
        assertEquals("Cảm ơn bạn rất nhiều", output.directTranslation)
        assertEquals("Cụm từ lịch sự và trang trọng được sử dụng phổ biến trong giao tiếp hàng ngày.", output.culturalContext)
    }

    @Test
    fun testKeyTermInsight_jsonSerialization() {
        val originalList = listOf(
            KeyTermInsight(
                translatedTerm = "Chúc may mắn",
                originalTerm = "Break a leg",
                type = "Idiom",
                explanation = "Thành ngữ chúc may mắn trong nghệ thuật sân khấu."
            ),
            KeyTermInsight(
                translatedTerm = "chùn bước",
                originalTerm = "get cold feet",
                type = "Idiom",
                explanation = "Cảm giác do dự sợ hãi trước thời khắc quan trọng."
            )
        )
        val json = KeyTermInsight.listToJson(originalList)
        val deserialized = KeyTermInsight.jsonToList(json)

        assertEquals(2, deserialized.size)
        assertEquals("Chúc may mắn", deserialized[0].translatedTerm)
        assertEquals("Break a leg", deserialized[0].originalTerm)
        assertEquals("Idiom", deserialized[0].type)
        assertEquals("chùn bước", deserialized[1].translatedTerm)
    }

    @Test
    fun testParseTranslation_withKeyTermsSection() {
        val raw = """
            Direct Translation: Chúc may mắn ở buổi thử giọng hôm nay nhé! Đừng có chùn bước đấy.

            Key Terms:
            - Chúc may mắn | Break a leg | Idiom | Thành ngữ chúc may mắn trong nghệ thuật sân khấu.
            - chùn bước | get cold feet | Idiom | Cảm giác lo lắng sợ hãi trước thời khắc quan trọng.

            Cultural Context:
            - **Ngữ cảnh**: Lời động viên thân mật trước buổi biểu diễn.
            - **Sắc thái**: Thân thiện, tích cực.
        """.trimIndent()

        val output = GeminiTranslatorService.parseTranslation(
            rawText = raw,
            sourceText = "Break a leg at the audition today! Don't get cold feet.",
            targetLanguage = "Vietnamese",
            isShortText = false
        )

        assertEquals("Chúc may mắn ở buổi thử giọng hôm nay nhé! Đừng có chùn bước đấy.", output.directTranslation)
        assertEquals(2, output.keyTerms.size)
        assertEquals("Chúc may mắn", output.keyTerms[0].translatedTerm)
        assertEquals("Break a leg", output.keyTerms[0].originalTerm)
        assertEquals("Idiom", output.keyTerms[0].type)
        assertEquals("chùn bước", output.keyTerms[1].translatedTerm)
        assertTrue(output.culturalContext.contains("- **Ngữ cảnh**: Lời động viên"))
    }

    @Test
    fun testParseTranslation_withEnableInsightFalse_extractsKeyTermsAndSuppressesCulturalContext() {
        val raw = """
            Direct Translation: Chúc may mắn ở buổi thử giọng hôm nay nhé! Đừng có chùn bước đấy.

            Key Terms:
            - Chúc may mắn | Break a leg | Idiom | Thành ngữ chúc may mắn trong nghệ thuật sân khấu.
            - chùn bước | get cold feet | Idiom | Cảm giác lo lắng sợ hãi trước thời khắc quan trọng.

            Cultural Context:
            - **Ngữ cảnh**: Lời động viên thân mật trước buổi biểu diễn.
        """.trimIndent()

        val output = GeminiTranslatorService.parseTranslation(
            rawText = raw,
            sourceText = "Break a leg at the audition today! Don't get cold feet.",
            targetLanguage = "Vietnamese",
            isShortText = false,
            enableInsight = false
        )

        assertEquals("Chúc may mắn ở buổi thử giọng hôm nay nhé! Đừng có chùn bước đấy.", output.directTranslation)
        assertEquals(2, output.keyTerms.size)
        assertEquals("Chúc may mắn", output.keyTerms[0].translatedTerm)
        assertEquals("chùn bước", output.keyTerms[1].translatedTerm)
        assertEquals("", output.culturalContext)
    }

    @Test
    fun testParseTranslation_withEnableInsightFalse_rawTextWithoutCulturalContextSection() {
        val raw = """
            Direct Translation: Cố lên bạn nhé, mọi chuyện rồi sẽ ổn thôi!

            Key Terms:
            - Cố lên | Hang in there | Phrasal Verb | Lời khuyên kiên trì vượt qua khó khăn.
        """.trimIndent()

        val output = GeminiTranslatorService.parseTranslation(
            rawText = raw,
            sourceText = "Hang in there, everything will be alright!",
            targetLanguage = "Vietnamese",
            isShortText = false,
            enableInsight = false
        )

        assertEquals("Cố lên bạn nhé, mọi chuyện rồi sẽ ổn thôi!", output.directTranslation)
        assertEquals(1, output.keyTerms.size)
        assertEquals("Cố lên", output.keyTerms[0].translatedTerm)
        assertEquals("Hang in there", output.keyTerms[0].originalTerm)
        assertEquals("", output.culturalContext)
    }
}



