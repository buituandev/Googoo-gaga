package com.techfox

import com.techfox.data.GeminiTranslatorService
import com.techfox.data.KeyTermInsight
import com.techfox.data.LanguageDetector
import com.techfox.data.TextSanitizer
import com.techfox.ui.components.FULL_LANGUAGE_LIST
import com.techfox.ui.components.POPULAR_LANGUAGES
import com.techfox.ui.components.detectLocaleFromText
import com.techfox.ui.components.resolveLocaleForLanguage
import java.util.Locale
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

    @Test
    fun testDetectLocaleFromText_withVariousLanguages() {
        // Japanese
        assertEquals(Locale.JAPANESE, detectLocaleFromText("木漏れ日"))
        assertEquals(Locale.JAPANESE, detectLocaleFromText("こんにちは"))
        assertEquals(Locale.JAPANESE, detectLocaleFromText("カタカナ"))

        // Korean
        assertEquals(Locale.KOREAN, detectLocaleFromText("안녕하세요"))
        assertEquals(Locale.KOREAN, detectLocaleFromText("눈치"))

        // Chinese
        assertEquals(Locale.SIMPLIFIED_CHINESE, detectLocaleFromText("你好世界"))

        // Russian / Cyrillic
        assertEquals("ru", detectLocaleFromText("Здравствуйте").language)

        // Arabic
        assertEquals("ar", detectLocaleFromText("مرحبا").language)

        // Thai
        assertEquals("th", detectLocaleFromText("สวัสดี").language)

        // Vietnamese
        assertEquals("vi", detectLocaleFromText("Xin chào bạn").language)
        assertEquals("vi", detectLocaleFromText("cố lên").language)

        // English / Latin default
        assertEquals(Locale.ENGLISH, detectLocaleFromText("break a leg"))
    }

    @Test
    fun testResolveLocaleForLanguage_withExplicitAndAutoLanguages() {
        assertEquals(Locale.FRENCH, resolveLocaleForLanguage("French"))
        assertEquals(Locale.GERMAN, resolveLocaleForLanguage("German"))
        assertEquals(Locale.JAPANESE, resolveLocaleForLanguage("Japanese"))
        assertEquals(Locale.ENGLISH, resolveLocaleForLanguage("English"))
        assertEquals("vi", resolveLocaleForLanguage("Vietnamese").language)

        // Auto mode with fallback text
        assertEquals(Locale.JAPANESE, resolveLocaleForLanguage("auto", "木漏れ日"))
        assertEquals(Locale.KOREAN, resolveLocaleForLanguage("auto", "눈치"))
        assertEquals("vi", resolveLocaleForLanguage("auto", "Xin chào").language)
        assertEquals(Locale.ENGLISH, resolveLocaleForLanguage("auto", "Break a leg"))
    }

    @Test
    fun testLanguageDetector_fallbackIdentification() {
        assertEquals("ja", LanguageDetector.identifyLanguageFallback("木漏れ日"))
        assertEquals("ko", LanguageDetector.identifyLanguageFallback("안녕하세요"))
        assertEquals("zh", LanguageDetector.identifyLanguageFallback("你好世界"))
        assertEquals("vi", LanguageDetector.identifyLanguageFallback("cố lên nhé"))
        assertEquals("ru", LanguageDetector.identifyLanguageFallback("Здравствуйте"))
        assertEquals("ar", LanguageDetector.identifyLanguageFallback("مرحبا"))
        assertEquals("th", LanguageDetector.identifyLanguageFallback("สวัสดี"))
        assertEquals("en", LanguageDetector.identifyLanguageFallback("Break a leg"))
    }

    @Test
    fun testIdentifyHello() {
        val detected = LanguageDetector.identify("hello")
        assertEquals("en", detected)
        assertTrue(LanguageDetector.isSameLanguage(detected, "English"))
        assertTrue(LanguageDetector.matchesTargetLanguage("hello", "English"))
        assertTrue(LanguageDetector.matchesTargetLanguage("Hello", "English"))
        assertTrue(LanguageDetector.matchesTargetLanguage("Hello world", "English"))
        assertFalse(LanguageDetector.matchesTargetLanguage("hello", "Vietnamese"))
        assertTrue(LanguageDetector.matchesTargetLanguage("xin chào", "Vietnamese"))
        assertFalse(LanguageDetector.matchesTargetLanguage("xin chào", "English"))
    }

    @Test
    fun testIsSameLanguage() {
        // Vietnamese match
        assertTrue(LanguageDetector.isSameLanguage("vi", "Vietnamese"))
        assertTrue(LanguageDetector.isSameLanguage("vietnamese", "Vietnamese"))
        assertTrue(LanguageDetector.isSameLanguage("vi", "Tiếng Việt"))
        assertTrue(LanguageDetector.isSameLanguage("vi", "vi"))

        // English match
        assertTrue(LanguageDetector.isSameLanguage("en", "English"))
        assertTrue(LanguageDetector.isSameLanguage("english", "English"))
        assertTrue(LanguageDetector.isSameLanguage("en", "Tiếng Anh"))

        // Japanese, French, Spanish match
        assertTrue(LanguageDetector.isSameLanguage("ja", "Japanese"))
        assertTrue(LanguageDetector.isSameLanguage("fr", "French"))
        assertTrue(LanguageDetector.isSameLanguage("es", "Spanish"))

        // Other world languages (Turkish, Swedish, Polish, Italian, Dutch, Greek, Russian, Arabic)
        assertTrue(LanguageDetector.isSameLanguage("tr", "Turkish"))
        assertTrue(LanguageDetector.isSameLanguage("sv", "Swedish"))
        assertTrue(LanguageDetector.isSameLanguage("pl", "Polish"))
        assertTrue(LanguageDetector.isSameLanguage("it", "Italian"))
        assertTrue(LanguageDetector.isSameLanguage("nl", "Dutch"))
        assertTrue(LanguageDetector.isSameLanguage("el", "Greek"))
        assertTrue(LanguageDetector.isSameLanguage("ru", "Russian"))
        assertTrue(LanguageDetector.isSameLanguage("ar", "Arabic"))

        // Different languages
        assertFalse(LanguageDetector.isSameLanguage("en", "Vietnamese"))
        assertFalse(LanguageDetector.isSameLanguage("vi", "English"))
        assertFalse(LanguageDetector.isSameLanguage("ja", "English"))
        assertFalse(LanguageDetector.isSameLanguage("tr", "German"))
        assertFalse(LanguageDetector.isSameLanguage("und", "Vietnamese"))
        assertFalse(LanguageDetector.isSameLanguage("auto", "Vietnamese"))
    }

    @Test
    fun testParseModelsJson_filtersGenerateContentModels() {
        val sampleJson = """
            {
              "models": [
                {
                  "name": "models/gemini-2.0-flash",
                  "supportedGenerationMethods": ["generateContent", "countTokens"]
                },
                {
                  "name": "models/gemini-1.5-pro",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/text-embedding-004",
                  "supportedGenerationMethods": ["embedContent"]
                }
              ]
            }
        """.trimIndent()

        val models = GeminiTranslatorService.parseModelsJson(sampleJson)
        assertEquals(2, models.size)
        assertEquals("gemini-2.0-flash", models[0])
        assertEquals("gemini-1.5-pro", models[1])
        assertFalse(models.contains("text-embedding-004"))
    }

    @Test
    fun testParseModelsJson_withEmptyOrInvalidJson() {
        assertEquals(emptyList<String>(), GeminiTranslatorService.parseModelsJson(""))
        assertEquals(emptyList<String>(), GeminiTranslatorService.parseModelsJson("{}"))
        assertEquals(emptyList<String>(), GeminiTranslatorService.parseModelsJson("invalid json"))
    }

    @Test
    fun testParseTranslation_keyTermWithEmbeddedContextWord_doesNotTruncate() {
        val raw = """
            Direct Translation: Bạn nên xem xét bối cảnh trước khi đưa ra quyết định.

            Key Terms:
            - bối cảnh | context | Vocabulary | Từ mang nghĩa ngữ cảnh (Context: dùng nhiều trong phân tích hoặc giao tiếp hàng ngày).
            - quyết định | decision | Vocabulary | Sự lựa chọn sau khi suy nghĩ kỹ.

            Cultural Context:
            - **Giao tiếp**: Nên cân nhắc kỹ lưỡng hoàn cảnh trước khi hành động.
        """.trimIndent()

        val output = GeminiTranslatorService.parseTranslation(
            rawText = raw,
            sourceText = "You should consider the context before making a decision.",
            targetLanguage = "Vietnamese",
            isShortText = false
        )

        assertEquals("Bạn nên xem xét bối cảnh trước khi đưa ra quyết định.", output.directTranslation)
        assertEquals(2, output.keyTerms.size)
        assertEquals("bối cảnh", output.keyTerms[0].translatedTerm)
        assertEquals("context", output.keyTerms[0].originalTerm)
        assertEquals("Vocabulary", output.keyTerms[0].type)
        assertEquals("Từ mang nghĩa ngữ cảnh (Context: dùng nhiều trong phân tích hoặc giao tiếp hàng ngày).", output.keyTerms[0].explanation)
        assertEquals("quyết định", output.keyTerms[1].translatedTerm)
        assertEquals("decision", output.keyTerms[1].originalTerm)
        assertEquals("Sự lựa chọn sau khi suy nghĩ kỹ.", output.keyTerms[1].explanation)
        assertTrue(output.culturalContext.contains("- **Giao tiếp**: Nên cân nhắc kỹ lưỡng hoàn cảnh"))
        assertFalse(output.culturalContext.contains("dùng nhiều trong phân tích"))
    }

    @Test
    fun testFullLanguageList_containsComprehensiveWorldLanguages() {
        assertTrue("Expected 100+ languages in FULL_LANGUAGE_LIST", FULL_LANGUAGE_LIST.size >= 100)
        assertEquals("English", FULL_LANGUAGE_LIST[0])
        assertEquals("Vietnamese", FULL_LANGUAGE_LIST[1])

        // Verify key world languages are included
        val keyLanguages = listOf(
            "Chinese (Simplified)", "Chinese (Traditional)", "Spanish", "French", "German",
            "Japanese", "Korean", "Italian", "Portuguese", "Russian", "Arabic", "Hindi",
            "Polish", "Turkish", "Dutch", "Swedish", "Greek", "Thai", "Indonesian"
        )
        for (lang in keyLanguages) {
            assertTrue("Expected $lang to be in FULL_LANGUAGE_LIST", FULL_LANGUAGE_LIST.contains(lang))
        }

        // Verify no duplicates
        assertEquals(FULL_LANGUAGE_LIST.size, FULL_LANGUAGE_LIST.distinct().size)

        // Verify all language entries are valid capitalized strings
        for (lang in FULL_LANGUAGE_LIST) {
            assertTrue("Language name should be at least 3 chars: $lang", lang.length >= 3)
            assertTrue("Language name should be capitalized: $lang", lang.first().isUpperCase())
        }
    }

    @Test
    fun testNormalizeLanguageCode_withVariousLanguagesAndDialects() {
        assertEquals("en", LanguageDetector.normalizeLanguageCode("English"))
        assertEquals("vi", LanguageDetector.normalizeLanguageCode("Vietnamese"))
        assertEquals("zh", LanguageDetector.normalizeLanguageCode("Chinese (Simplified)"))
        assertEquals("zh", LanguageDetector.normalizeLanguageCode("Chinese (Traditional)"))
        assertEquals("pt", LanguageDetector.normalizeLanguageCode("Portuguese (Brazil)"))
        assertEquals("pl", LanguageDetector.normalizeLanguageCode("Polish"))
        assertEquals("tr", LanguageDetector.normalizeLanguageCode("Turkish"))
        assertEquals("nl", LanguageDetector.normalizeLanguageCode("Dutch"))
        assertEquals("sv", LanguageDetector.normalizeLanguageCode("Swedish"))
        assertEquals("el", LanguageDetector.normalizeLanguageCode("Greek"))
    }

    @Test
    fun testResolveLocaleForLanguage_withVariousLanguages() {
        assertEquals(Locale.ENGLISH, resolveLocaleForLanguage("English"))
        assertEquals(Locale.forLanguageTag("vi-VN"), resolveLocaleForLanguage("Vietnamese"))
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolveLocaleForLanguage("Chinese (Simplified)"))
        assertEquals(Locale.TRADITIONAL_CHINESE, resolveLocaleForLanguage("Chinese (Traditional)"))
        assertEquals("pt", resolveLocaleForLanguage("Portuguese (Brazil)").language)
        assertEquals("pl", resolveLocaleForLanguage("Polish").language)
        assertEquals("tr", resolveLocaleForLanguage("Turkish").language)
        assertEquals("nl", resolveLocaleForLanguage("Dutch").language)
        assertEquals("sv", resolveLocaleForLanguage("Swedish").language)
    }
}



