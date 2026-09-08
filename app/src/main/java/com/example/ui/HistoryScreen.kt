package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.TranslationEntity
import com.example.data.highlightFuzzyMatch
import com.example.data.searchTranslations
import com.example.ui.theme.PillShape
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(
    state: UiState,
    onHistoryItemSelected: (TranslationEntity) -> Unit,
    onDeleteItem: (TranslationEntity) -> Unit,
    onClearAllHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberContainedSearchBarState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val query = textFieldState.text.toString().trim()
    val filteredHistory = remember(state.history, query) {
        if (query.isBlank()) {
            state.history
        } else {
            state.history.searchTranslations(query)
        }
    }

    val appBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors(
        scrolledAppBarContainerColor = MaterialTheme.colorScheme.background,
        searchBarColors = SearchBarDefaults.containedColors(state = searchBarState)
    )


    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            colors = appBarWithSearchColors.searchBarColors.inputFieldColors,
            onSearch = {
                scope.launch { searchBarState.animateToCollapsed() }
            },
            placeholder = {
                Text(
                    modifier = Modifier.clearAndSetSemantics {},
                    text = stringResource(R.string.search_history_placeholder)
                )
            },
            leadingIcon = {
                if (searchBarState.targetValue == SearchBarValue.Expanded) {
                    IconButton(onClick = {
                        scope.launch { searchBarState.animateToCollapsed() }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.cd_search),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = stringResource(R.string.cd_clear_search)
                        )
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    AppBarWithSearch(
                        scrollBehavior = scrollBehavior,
                        state = searchBarState,
                        colors = appBarWithSearchColors,
                        inputField = inputField,
                        actions = {
                            if (state.history.isNotEmpty()) {
                                IconButton(
                                    onClick = { showClearConfirmDialog = true },
                                    modifier = Modifier.testTag("clear_all_history_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteSweep,
                                        contentDescription = stringResource(R.string.cd_clear_history),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )

                    ExpandedFullScreenContainedSearchBar(
                        state = searchBarState,
                        inputField = inputField,
                        colors = appBarWithSearchColors.searchBarColors,
                    ) {
                        HistoryListContent(
                            items = filteredHistory,
                            onItemClick = { item ->
                                scope.launch { searchBarState.animateToCollapsed() }
                                onHistoryItemSelected(item)
                            },
                            onDeleteItem = onDeleteItem,
                            isSearching = query.isNotBlank(),
                            searchQuery = query
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        HistoryListContent(
            items = filteredHistory,
            onItemClick = onHistoryItemSelected,
            onDeleteItem = onDeleteItem,
            isSearching = query.isNotBlank(),
            searchQuery = query,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_history_title)) },
            text = { Text(stringResource(R.string.dialog_clear_history_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllHistory()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = PillShape
                ) {
                    Text(stringResource(R.string.btn_clear_all))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    shape = PillShape
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryListContent(
    items: List<TranslationEntity>,
    modifier: Modifier = Modifier,
    onItemClick: (TranslationEntity) -> Unit,
    onDeleteItem: (TranslationEntity) -> Unit,
    isSearching: Boolean,
    searchQuery: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSearching) Icons.Rounded.Search else Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isSearching) stringResource(R.string.no_search_results_title) else stringResource(
                        R.string.empty_history_title
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isSearching) stringResource(R.string.no_search_results_desc) else stringResource(
                        R.string.empty_history_desc
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        val highlightStyle = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.id }
            ) { index, item ->
                val hasKeyTerms = item.keyTerms.isNotEmpty()
                val dateStr = remember(item.timestamp) {
                    SimpleDateFormat(
                        "MMM d, HH:mm",
                        Locale.getDefault()
                    ).format(Date(item.timestamp))
                }

                val sourceAnnotated = remember(item.sourceText, searchQuery) {
                    if (searchQuery.isNotBlank()) {
                        highlightFuzzyMatch(item.sourceText, searchQuery, highlightStyle)
                    } else {
                        AnnotatedString(item.sourceText)
                    }
                }

                val translationAnnotated = remember(item.directTranslation, searchQuery) {
                    if (searchQuery.isNotBlank()) {
                        highlightFuzzyMatch(item.directTranslation, searchQuery, highlightStyle)
                    } else {
                        AnnotatedString(item.directTranslation)
                    }
                }

                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(
                        index = index,
                        count = items.size
                    ),
                    onClick = { onItemClick(item) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_item_${item.id}"),
                    overlineContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = item.targetLanguage,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasKeyTerms) {
                                Surface(
                                    shape = PillShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 6.dp,
                                            vertical = 2.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.key_terms_count,
                                                item.keyTerms.size
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    },
                    content = {
                        Text(
                            text = sourceAnnotated,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = translationAnnotated,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = { onDeleteItem(item) },
                            modifier = Modifier.testTag("delete_history_item_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_delete_item),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}
