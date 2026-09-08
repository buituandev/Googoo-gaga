package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.KeyTermInsight
import com.example.ui.FormattedMarkdownText
import com.example.ui.theme.PillShape

private data class TermSpan(
    val start: Int,
    val end: Int,
    val term: KeyTermInsight
)

/**
 * Interactive translation text view that highlights key idioms, slang, and cultural phrases.
 * Tapping a highlighted term opens the detailed insight dialog.
 */
@Composable
fun InteractiveTranslationText(
    text: String,
    keyTerms: List<KeyTermInsight>,
    onTermClicked: (KeyTermInsight) -> Unit,
    modifier: Modifier = Modifier
) {
    if (keyTerms.isEmpty()) {
        FormattedMarkdownText(text = text, modifier = modifier)
        return
    }

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    // Find non-overlapping occurrences of key terms in the translated text, prioritizing longer phrases
    val spans = remember(text, keyTerms) {
        val list = mutableListOf<TermSpan>()
        val lowerText = text.lowercase()

        // Prioritize longer idioms and compound phrases before single words
        val sortedTerms = keyTerms.sortedByDescending { it.translatedTerm.length }

        for (term in sortedTerms) {
            val query = term.translatedTerm.trim()
                .trim('"', '\'', '“', '”', '`', '.', ',', '!', '?', ';', ':')
                .lowercase()
            if (query.isBlank()) continue

            var searchFrom = 0
            while (searchFrom < text.length) {
                val index = lowerText.indexOf(query, searchFrom)
                if (index == -1) break

                val endIndex = index + query.length
                // Ensure span does not collide with existing spans
                val collides = list.any { existing ->
                    (index in existing.start until existing.end) || (endIndex in (existing.start + 1)..existing.end)
                }

                if (!collides) {
                    list.add(TermSpan(index, endIndex, term))
                }
                searchFrom = endIndex
            }
        }
        list.sortedBy { it.start }
    }

    if (spans.isEmpty()) {
        FormattedMarkdownText(text = text, modifier = modifier)
        return
    }

    val annotatedString = remember(text, spans, primaryContainer, onPrimaryContainer, primaryColor) {
        buildAnnotatedString {
            var cursor = 0
            for (span in spans) {
                if (span.start > cursor) {
                    append(text.substring(cursor, span.start))
                }

                val linkAnnotation = LinkAnnotation.Clickable(
                    tag = span.term.translatedTerm,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            background = primaryContainer.copy(alpha = 0.7f),
                            color = onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline
                        )
                    ),
                    linkInteractionListener = {
                        onTermClicked(span.term)
                    }
                )

                withLink(linkAnnotation) {
                    append(text.substring(span.start, span.end))
                }

                cursor = span.end
            }

            if (cursor < text.length) {
                append(text.substring(cursor))
            }
        }
    }

    Column(modifier = modifier) {
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = 26.sp,
                color = onSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("interactive_translation_text")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive highlight hint pill
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.hint_interactive_highlight),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
