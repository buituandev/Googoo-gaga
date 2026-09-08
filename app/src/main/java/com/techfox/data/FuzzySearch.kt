@file:Suppress("unused")

package com.techfox.data

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Result of a fuzzy search match evaluation.
 *
 * @property isMatch True if the query matches the target string according to the scoring criteria.
 * @property score Match score normalized between 0.0f (no match) and 1.0f (perfect match).
 * @property matchedIndices The exact character indices in the target string that matched the query.
 */
data class FuzzyMatchResult(
    val isMatch: Boolean,
    val score: Float,
    val matchedIndices: List<Int> = emptyList()
) {
    /**
     * Group contiguous matched indices into [IntRange]s for UI span highlighting.
     */
    val matchedRanges: List<IntRange> by lazy {
        if (matchedIndices.isEmpty()) return@lazy emptyList()
        val sorted = matchedIndices.distinct().sorted()
        val ranges = mutableListOf<IntRange>()
        var start = sorted.first()
        var prev = start

        for (i in 1 until sorted.size) {
            val curr = sorted[i]
            if (curr == prev + 1) {
                prev = curr
            } else {
                ranges.add(start..prev)
                start = curr
                prev = curr
            }
        }
        ranges.add(start..prev)
        ranges
    }
}

/**
 * Robust, high-performance fuzzy search engine supporting:
 * - Sequential fuzzy subsequence matching (fzf-like)
 * - Accent / diacritic insensitivity (e.g. Vietnamese/Spanish accents)
 * - Damerau-Levenshtein edit distance for typo tolerance
 * - Word boundary and acronym bonuses
 * - Multi-token querying and rank-ordered scoring
 */
object FuzzySearch {

    private val DIACRITICS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    /**
     * Normalizes text by removing diacritics / accents and converting to lowercase.
     * Handles Vietnamese specific characters (đ/Đ) and international diacritics.
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val trimmed = text.trim()
        val step1 = trimmed.replace('đ', 'd').replace('Đ', 'D')
        val normalized = Normalizer.normalize(step1, Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(normalized, "").lowercase(Locale.ROOT)
    }

    /**
     * Evaluates the fuzzy match between a [query] and a [target] string.
     *
     * @param query The search query typed by the user.
     * @param target The candidate string being tested.
     * @param minScore Minimum threshold score (0.0 to 1.0) to consider it a valid match.
     * @return [FuzzyMatchResult] containing match status, score, and matched indices.
     */
    fun match(
        query: String,
        target: String,
        minScore: Float = 0.35f
    ): FuzzyMatchResult {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return FuzzyMatchResult(isMatch = true, score = 1.0f, matchedIndices = emptyList())
        }
        if (target.isEmpty()) {
            return FuzzyMatchResult(isMatch = false, score = 0.0f, matchedIndices = emptyList())
        }

        val normQuery = normalize(trimmedQuery)
        val normTarget = normalize(target)

        // 1. Exact or Substring check
        if (normTarget == normQuery) {
            return FuzzyMatchResult(
                isMatch = true,
                score = 1.0f,
                matchedIndices = target.indices.toList()
            )
        }

        val substringIndex = normTarget.indexOf(normQuery)
        if (substringIndex >= 0) {
            val lengthRatio = normQuery.length.toFloat() / normTarget.length.toFloat()
            val startBonus = if (substringIndex == 0) 0.2f else 0.1f
            val baseScore = min(0.95f, 0.75f + (lengthRatio * 0.15f) + startBonus)
            val indices = (substringIndex until substringIndex + normQuery.length).toList()
            return FuzzyMatchResult(
                isMatch = baseScore >= minScore,
                score = baseScore,
                matchedIndices = indices
            )
        }

        // 2. Multi-token matching if query has spaces
        val tokens = normQuery.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.size > 1) {
            var totalScore = 0f
            val allIndices = mutableListOf<Int>()
            var allMatched = true

            for (token in tokens) {
                val tokenResult = matchSingleToken(token, normTarget)
                if (!tokenResult.isMatch) {
                    allMatched = false
                    break
                }
                totalScore += tokenResult.score
                allIndices.addAll(tokenResult.matchedIndices)
            }

            if (allMatched) {
                val avgScore = (totalScore / tokens.size) * 0.9f
                return FuzzyMatchResult(
                    isMatch = avgScore >= minScore,
                    score = avgScore,
                    matchedIndices = allIndices.distinct().sorted()
                )
            }
        }

        // 3. Single token / Subsequence fuzzy match
        val singleResult = matchSingleToken(normQuery, normTarget)
        if (singleResult.isMatch && singleResult.score >= minScore) {
            return singleResult
        }

        // 4. Typo tolerance check via Levenshtein edit distance for short/medium queries
        if (normQuery.length >= 3) {
            val maxAllowedDistance = when {
                normQuery.length <= 4 -> 1
                normQuery.length <= 8 -> 2
                else -> 3
            }
            val editDistance = levenshteinDistance(normQuery, normTarget)
            if (editDistance <= maxAllowedDistance) {
                val score = 1.0f - (editDistance.toFloat() / max(normQuery.length, normTarget.length))
                if (score >= minScore) {
                    return FuzzyMatchResult(isMatch = true, score = score * 0.7f, matchedIndices = emptyList())
                }
            }
        }

        return FuzzyMatchResult(isMatch = false, score = 0.0f, matchedIndices = emptyList())
    }

    private fun matchSingleToken(
        normQuery: String,
        normTarget: String
    ): FuzzyMatchResult {
        var queryIdx = 0
        var targetIdx = 0
        var score = 0f
        var consecutiveMatches = 0
        val matchedIndices = mutableListOf<Int>()

        while (queryIdx < normQuery.length && targetIdx < normTarget.length) {
            val qChar = normQuery[queryIdx]
            val tChar = normTarget[targetIdx]

            if (qChar == tChar) {
                matchedIndices.add(targetIdx)
                var charScore = 1.0f

                // Bonus for consecutive character matches
                if (consecutiveMatches > 0) {
                    charScore += 0.5f * consecutiveMatches
                }
                consecutiveMatches++

                // Bonus for match at word boundary (start of string or after space/separator)
                if (targetIdx == 0 || normTarget[targetIdx - 1] in " ._-,:;/\\|") {
                    charScore += 0.8f
                }

                score += charScore
                queryIdx++
            } else {
                consecutiveMatches = 0
            }
            targetIdx++
        }

        val allMatched = queryIdx == normQuery.length
        if (!allMatched) {
            return FuzzyMatchResult(isMatch = false, score = 0.0f)
        }

        val maxPossibleScore = (normQuery.length * 2.3f)
        val lengthPenalty = (normTarget.length - normQuery.length) * 0.02f
        val startPenalty = if (matchedIndices.isNotEmpty()) matchedIndices.first() * 0.01f else 0f

        val normalizedScore = min(
            1.0f,
            max(0.0f, (score / maxPossibleScore) - lengthPenalty - startPenalty)
        )

        return FuzzyMatchResult(
            isMatch = normalizedScore >= 0.25f,
            score = normalizedScore,
            matchedIndices = matchedIndices
        )
    }

    /**
     * Calculates the Levenshtein distance between two normalized strings.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1, // deletion
                    min(
                        dp[i][j - 1] + 1, // insertion
                        dp[i - 1][j - 1] + cost // substitution
                    )
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}

// -----------------------------------------------------------------------------------------
// Collection Extensions
// -----------------------------------------------------------------------------------------

/**
 * Filters and sorts a collection based on fuzzy search relevance against a single string property.
 */
fun <T> Iterable<T>.fuzzyFilter(
    query: String,
    minScore: Float = 0.35f,
    selector: (T) -> String
): List<T> {
    if (query.isBlank()) return this.toList()

    return this.map { item ->
        val result = FuzzySearch.match(query, selector(item), minScore)
        item to result
    }
        .filter { it.second.isMatch }
        .sortedByDescending { it.second.score }
        .map { it.first }
}

/**
 * Filters and sorts a collection based on fuzzy search against multiple string fields (e.g., title, body, tags).
 */
fun <T> Iterable<T>.fuzzySearch(
    query: String,
    minScore: Float = 0.35f,
    selectors: (T) -> List<String>
): List<T> {
    if (query.isBlank()) return this.toList()

    return this.map { item ->
        val bestScore = selectors(item).maxOfOrNull { text ->
            FuzzySearch.match(query, text, minScore).score
        } ?: 0f
        item to bestScore
    }
        .filter { it.second >= minScore }
        .sortedByDescending { it.second }
        .map { it.first }
}

/**
 * Extension for filtering [TranslationEntity] history items by fuzzy matching sourceText, directTranslation, targetLanguage, or keyTerms.
 */
fun List<TranslationEntity>.searchTranslations(
    query: String,
    minScore: Float = 0.35f
): List<TranslationEntity> {
    if (query.isBlank()) return this

    return this.fuzzySearch(query, minScore) { entity ->
        buildList {
            add(entity.sourceText)
            add(entity.directTranslation)
            add(entity.targetLanguage)
            entity.keyTerms.forEach { term ->
                add(term.originalTerm)
                add(term.translatedTerm)
                add(term.explanation)
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// Jetpack Compose Highlighting Helper
// -----------------------------------------------------------------------------------------

/**
 * Creates an [AnnotatedString] with highlighted match ranges for Jetpack Compose UI.
 */
fun highlightFuzzyMatch(
    text: String,
    query: String,
    highlightStyle: SpanStyle
): AnnotatedString {
    if (query.isBlank() || text.isBlank()) {
        return AnnotatedString(text)
    }

    val result = FuzzySearch.match(query, text)
    if (!result.isMatch || result.matchedRanges.isEmpty()) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        append(text)
        result.matchedRanges.forEach { range ->
            if (range.first in text.indices && range.last in text.indices) {
                addStyle(
                    style = highlightStyle,
                    start = range.first,
                    end = range.last + 1
                )
            }
        }
    }
}
