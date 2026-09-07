package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.example.data.FuzzySearch
import com.example.data.KeyTermInsight
import com.example.data.TranslationEntity
import com.example.data.fuzzyFilter
import com.example.data.fuzzySearch
import com.example.data.highlightFuzzyMatch
import com.example.data.searchTranslations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [FuzzySearch] engine and associated collection / compose extension functions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FuzzySearchTest {

    // -----------------------------------------------------------------------------------------
    // Normalization & Diacritic Removal Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun testNormalize_removesVietnameseDiacriticsAndPreservesD() {
        assertEquals("tieng viet", FuzzySearch.normalize("Tiếng Việt"))
        assertEquals("cam on ban rat nhieu", FuzzySearch.normalize("Cảm ơn bạn rất nhiều"))
        assertEquals("da nang", FuzzySearch.normalize("Đà Nẵng"))
        assertEquals("dien thoai", FuzzySearch.normalize("Điện thoại"))
        assertEquals("dong ho", FuzzySearch.normalize("Đồng hồ"))
    }

    @Test
    fun testNormalize_removesInternationalAccents() {
        assertEquals("cafe resume", FuzzySearch.normalize("Café résumé"))
        assertEquals("nino", FuzzySearch.normalize("Niño"))
        assertEquals("uber", FuzzySearch.normalize("Über"))
        assertEquals("francais", FuzzySearch.normalize("Français"))
    }

    @Test
    fun testNormalize_handlesEmptyAndBlank() {
        assertEquals("", FuzzySearch.normalize(""))
        assertEquals("", FuzzySearch.normalize("   "))
    }

    // -----------------------------------------------------------------------------------------
    // Exact and Substring Matching Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun testMatch_exactMatchReturnsFullScore() {
        val result = FuzzySearch.match("hello", "Hello")
        assertTrue(result.isMatch)
        assertEquals(1.0f, result.score, 0.001f)
        assertEquals(listOf(0, 1, 2, 3, 4), result.matchedIndices)
    }

    @Test
    fun testMatch_accentInsensitiveExactMatch() {
        val result = FuzzySearch.match("tieng viet", "Tiếng Việt")
        assertTrue(result.isMatch)
        assertEquals(1.0f, result.score, 0.001f)
    }

    @Test
    fun testMatch_substringMatch() {
        val result = FuzzySearch.match("world", "Hello world of coding")
        assertTrue(result.isMatch)
        assertTrue("Substring score should be high", result.score >= 0.75f)
        assertEquals(listOf(6, 7, 8, 9, 10), result.matchedIndices)
    }

    @Test
    fun testMatch_emptyQueryOrTarget() {
        val emptyQueryResult = FuzzySearch.match("", "Some text")
        assertTrue(emptyQueryResult.isMatch)
        assertEquals(1.0f, emptyQueryResult.score, 0.001f)

        val emptyTargetResult = FuzzySearch.match("search", "")
        assertFalse(emptyTargetResult.isMatch)
        assertEquals(0.0f, emptyTargetResult.score, 0.001f)
    }

    // -----------------------------------------------------------------------------------------
    // Fuzzy Subsequence & Word Boundary Matching Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun testMatch_acronymAndInitialismMatching() {
        val result = FuzzySearch.match("gt", "Gemini Translator")
        assertTrue("Acronym query 'gt' should match 'Gemini Translator'", result.isMatch)
        assertTrue(result.score > 0.35f)
    }

    @Test
    fun testMatch_nonContiguousSubsequenceMatching() {
        val result = FuzzySearch.match("hellwrld", "Hello World")
        assertTrue("Subsequence characters should match", result.isMatch)
    }

    @Test
    fun testMatch_multiTokenMatchingWithSpaces() {
        val result = FuzzySearch.match("hello viet", "Hello everyone in Vietnam")
        assertTrue("Multi-token query should match across target words", result.isMatch)
        assertTrue(result.score > 0.4f)
    }

    @Test
    fun testMatch_completeMismatch() {
        val result = FuzzySearch.match("xyz123", "Good morning and have a nice day")
        assertFalse(result.isMatch)
        assertEquals(0.0f, result.score, 0.001f)
    }

    // -----------------------------------------------------------------------------------------
    // Levenshtein Distance & Typo Tolerance Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun testLevenshteinDistance() {
        assertEquals(0, FuzzySearch.levenshteinDistance("kitten", "kitten"))
        assertEquals(1, FuzzySearch.levenshteinDistance("cat", "hat"))
        assertEquals(3, FuzzySearch.levenshteinDistance("kitten", "sitting"))
        assertEquals(5, FuzzySearch.levenshteinDistance("", "hello"))
        assertEquals(4, FuzzySearch.levenshteinDistance("test", ""))
    }

    @Test
    fun testMatch_typoToleranceOnKeywords() {
        // Minor typo 'gemni' for 'gemini'
        val result = FuzzySearch.match("gemni", "gemini")
        assertTrue("Typo 'gemni' should match 'gemini'", result.isMatch)
    }

    // -----------------------------------------------------------------------------------------
    // Collection Extension Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun testFuzzyFilter_filtersAndSortsByRelevance() {
        val fruits = listOf("Banana", "Apple", "Pineapple", "Grape", "Appletini")
        val filtered = fruits.fuzzyFilter("app") { it }

        assertTrue(filtered.contains("Apple"))
        assertTrue(filtered.contains("Pineapple"))
        assertTrue(filtered.contains("Appletini"))
        assertFalse(filtered.contains("Banana"))

        // Exact / prefix match "Apple" should rank highest
        assertEquals("Apple", filtered.first())
    }

    @Test
    fun testFuzzySearch_multiFieldSearch() {
        data class MockItem(val name: String, val desc: String, val tag: String)

        val items = listOf(
            MockItem("Setup Guide", "How to install the application", "docs"),
            MockItem("API Reference", "Details about translation endpoints", "dev"),
            MockItem("Troubleshooting", "Fix common errors and issues", "help")
        )

        val found = items.fuzzySearch("endpoint") { listOf(it.name, it.desc, it.tag) }
        assertEquals(1, found.size)
        assertEquals("API Reference", found.first().name)
    }

    // -----------------------------------------------------------------------------------------
    // Translation History Search Extension Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun testSearchTranslations_matchesSourceText() {
        val history = listOf(
            createTranslation(id = 1, source = "Good morning friends", translation = "Chào buổi sáng các bạn", lang = "Vietnamese"),
            createTranslation(id = 2, source = "Where is the train station?", translation = "Ga xe lửa ở đâu?", lang = "Vietnamese"),
            createTranslation(id = 3, source = "Thank you very much", translation = "Cảm ơn bạn rất nhiều", lang = "Vietnamese")
        )

        val results = history.searchTranslations("morning")
        assertEquals(1, results.size)
        assertEquals(1L, results.first().id)
    }

    @Test
    fun testSearchTranslations_matchesDirectTranslationWithDiacritics() {
        val history = listOf(
            createTranslation(id = 1, source = "Hello", translation = "Xin chào", lang = "Vietnamese"),
            createTranslation(id = 2, source = "Thank you", translation = "Cảm ơn", lang = "Vietnamese"),
            createTranslation(id = 3, source = "Goodbye", translation = "Tạm biệt", lang = "Vietnamese")
        )

        // Searching without tone marks "cam on" matches "Cảm ơn"
        val results = history.searchTranslations("cam on")
        assertEquals(1, results.size)
        assertEquals(2L, results.first().id)
    }

    @Test
    fun testSearchTranslations_matchesTargetLanguage() {
        val history = listOf(
            createTranslation(id = 1, source = "Hello", translation = "Bonjour", lang = "French"),
            createTranslation(id = 2, source = "Hello", translation = "Xin chào", lang = "Vietnamese"),
            createTranslation(id = 3, source = "Hello", translation = "Hola", lang = "Spanish")
        )

        val results = history.searchTranslations("French")
        assertEquals(1, results.size)
        assertEquals(1L, results.first().id)
    }

    @Test
    fun testSearchTranslations_matchesKeyTerms() {
        val history = listOf(
            createTranslation(
                id = 1,
                source = "We need synergy in this collaboration",
                translation = "Chúng ta cần sự hiệp lực trong lần hợp tác này",
                lang = "Vietnamese",
                keyTerms = listOf(
                    KeyTermInsight(
                        originalTerm = "synergy",
                        translatedTerm = "Hiệp lực",
                        explanation = "Corporate buzzword"
                    )
                )
            ),
            createTranslation(id = 2, source = "Simple test", translation = "Thử nghiệm đơn giản", lang = "Vietnamese")
        )

        val results = history.searchTranslations("hiep luc")
        assertEquals(1, results.size)
        assertEquals(1L, results.first().id)
    }

    // -----------------------------------------------------------------------------------------
    // Jetpack Compose Highlighting Helper Tests
    // -----------------------------------------------------------------------------------------

    @Test
    fun testHighlightFuzzyMatch_appliesSpanStyleCorrectly() {
        val text = "Gemini AI Translator"
        val style = SpanStyle(color = Color.Red)

        val annotated = highlightFuzzyMatch(text, "AI", style)
        assertEquals(text, annotated.text)
        assertTrue("AnnotatedString should contain style spans", annotated.spanStyles.isNotEmpty())
    }

    @Test
    fun testHighlightFuzzyMatch_emptyQueryReturnsPlainText() {
        val text = "No highlighting needed"
        val style = SpanStyle(color = Color.Blue)

        val annotated = highlightFuzzyMatch(text, "", style)
        assertEquals(text, annotated.text)
        assertEquals(0, annotated.spanStyles.size)
    }

    // -----------------------------------------------------------------------------------------
    // Helper Methods
    // -----------------------------------------------------------------------------------------

    private fun createTranslation(
        id: Long,
        source: String,
        translation: String,
        lang: String,
        keyTerms: List<KeyTermInsight> = emptyList()
    ): TranslationEntity {
        return TranslationEntity(
            id = id,
            sourceText = source,
            directTranslation = translation,
            targetLanguage = lang,
            culturalContext = "Mock context",
            keyTermsJson = KeyTermInsight.listToJson(keyTerms),
            timestamp = System.currentTimeMillis()
        )
    }
}
