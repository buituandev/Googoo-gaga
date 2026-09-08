package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * Renders AI markdown text cleanly in Compose using multiplatform-markdown-renderer-m3.
 */
@Composable
fun FormattedMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val markdownState = rememberMarkdownState(text)
    Markdown(
        markdownState = markdownState,
        modifier = modifier,
    )
}
