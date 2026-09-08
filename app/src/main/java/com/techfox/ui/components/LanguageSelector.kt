package com.techfox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techfox.R
import com.techfox.ui.theme.CardShape
import com.techfox.ui.theme.PillShape
import com.techfox.ui.theme.TextFieldShape
import java.util.Locale

/**
 * Pre-indexed immutable data holder for ultra-fast zero-allocation search filtering.
 */
@Immutable
data class SearchableLanguage(
    val displayName: String,
    val searchKey: String
)

/**
 * Top popular languages pinned at the top of the selector (pre-cached static list).
 */
val POPULAR_LANGUAGES: List<String> = listOf(
    "English",
    "Vietnamese",
    "Spanish",
    "French",
    "German",
    "Chinese (Simplified)",
    "Chinese (Traditional)",
    "Japanese",
    "Korean",
    "Italian",
    "Portuguese",
    "Russian",
    "Arabic",
    "Hindi",
    "Indonesian",
    "Thai"
)

/**
 * All other world languages sorted alphabetically (pre-cached static list).
 */
val ALL_OTHER_LANGUAGES: List<String> = listOf(
    "Afrikaans",
    "Albanian",
    "Amharic",
    "Armenian",
    "Assamese",
    "Aymara",
    "Azerbaijani",
    "Bambara",
    "Basque",
    "Belarusian",
    "Bengali",
    "Bhojpuri",
    "Bosnian",
    "Bulgarian",
    "Burmese",
    "Catalan",
    "Cebuano",
    "Chichewa",
    "Corsican",
    "Croatian",
    "Czech",
    "Danish",
    "Dhivehi",
    "Dogri",
    "Dutch",
    "Esperanto",
    "Estonian",
    "Ewe",
    "Filipino",
    "Finnish",
    "Frisian",
    "Galician",
    "Ganda",
    "Georgian",
    "Greek",
    "Guarani",
    "Gujarati",
    "Haitian Creole",
    "Hausa",
    "Hawaiian",
    "Hebrew",
    "Hmong",
    "Hungarian",
    "Icelandic",
    "Igbo",
    "Ilocano",
    "Irish",
    "Javanese",
    "Kannada",
    "Kazakh",
    "Khmer",
    "Kinyarwanda",
    "Konkani",
    "Krio",
    "Kurdish (Kurmanji)",
    "Kurdish (Sorani)",
    "Kyrgyz",
    "Lao",
    "Latin",
    "Latvian",
    "Lingala",
    "Lithuanian",
    "Luganda",
    "Luxembourgish",
    "Macedonian",
    "Maithili",
    "Malagasy",
    "Malay",
    "Malayalam",
    "Maltese",
    "Maori",
    "Marathi",
    "Meiteilon (Manipuri)",
    "Mizo",
    "Mongolian",
    "Nepali",
    "Northern Sotho",
    "Norwegian (Bokmål)",
    "Norwegian (Nynorsk)",
    "Odia (Oriya)",
    "Oromo",
    "Pashto",
    "Persian",
    "Polish",
    "Portuguese (Brazil)",
    "Portuguese (Portugal)",
    "Punjabi",
    "Quechua",
    "Romanian",
    "Samoan",
    "Sanskrit",
    "Scottish Gaelic",
    "Sepedi",
    "Serbian",
    "Sesotho",
    "Shona",
    "Sindhi",
    "Sinhala",
    "Slovak",
    "Slovenian",
    "Somali",
    "Sundanese",
    "Swahili",
    "Swedish",
    "Tagalog",
    "Tajik",
    "Tamil",
    "Tatar",
    "Telugu",
    "Tigrinya",
    "Tsonga",
    "Turkish",
    "Turkmen",
    "Twi",
    "Ukrainian",
    "Urdu",
    "Uyghur",
    "Uzbek",
    "Welsh",
    "Xhosa",
    "Yiddish",
    "Yoruba",
    "Zulu"
)

/**
 * Full comprehensive list of 130+ world languages (pre-cached static list).
 */
val FULL_LANGUAGE_LIST: List<String> = POPULAR_LANGUAGES + ALL_OTHER_LANGUAGES

/**
 * Pre-indexed static list with lowercased search keys for zero-allocation searching.
 */
private val PRE_INDEXED_LANGUAGES: List<SearchableLanguage> = FULL_LANGUAGE_LIST.map {
    SearchableLanguage(displayName = it, searchKey = it.lowercase(Locale.ROOT))
}

/**
 * Reusable high-performance language selector with LazyColumn virtualization, search filtering,
 * cached list slices, and a custom language input dialog.
 */
@Composable
fun LanguageDropdownSelector(
    targetLanguage: String,
    onTargetLanguageChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    availableLanguages: List<String> = FULL_LANGUAGE_LIST
) {
    var showSelectionDialog by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customLanguageInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val isSearching = searchQuery.isNotBlank()

    // Pre-cache filtered languages using pre-indexed keys
    val filteredLanguages = remember(searchQuery, availableLanguages) {
        if (searchQuery.isBlank()) {
            availableLanguages
        } else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            val availableSet = availableLanguages.toSet()
            PRE_INDEXED_LANGUAGES
                .filter { it.displayName in availableSet && it.searchKey.contains(q) }
                .map { it.displayName }
        }
    }

    // Pre-cache section splits outside LazyColumn scope
    val popularInList = remember(availableLanguages) {
        val popSet = POPULAR_LANGUAGES.toSet()
        availableLanguages.filter { it in popSet }
    }
    val remainingInList = remember(availableLanguages, popularInList) {
        val popSet = popularInList.toSet()
        availableLanguages.filter { it !in popSet }
    }

    val handleSelectLanguage = remember(onTargetLanguageChanged) {
        { selected: String ->
            onTargetLanguageChanged(selected)
            showSelectionDialog = false
            searchQuery = ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("target_language_dropdown")
    ) {
        OutlinedTextField(
            value = targetLanguage,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_target_language)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                IconButton(onClick = { showSelectionDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = "Select Language",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            interactionSource = remember { MutableInteractionSource() }
                .also { interactionSource ->
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                showSelectionDialog = true
                            }
                        }
                    }
                }
        )
    }

    // High-performance Language Selection Dialog with LazyColumn
    if (showSelectionDialog) {
        val listState = rememberLazyListState()

        LaunchedEffect(searchQuery) {
            if (searchQuery.isNotBlank()) {
                listState.scrollToItem(0)
            }
        }

        Dialog(
            onDismissRequest = {
                showSelectionDialog = false
                searchQuery = ""
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.82f),
                shape = CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp, bottom = 8.dp)
                ) {
                    // Header: Title & Close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_target_language),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = {
                            showSelectionDialog = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        placeholder = { Text(stringResource(R.string.search_language_placeholder)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = TextFieldShape
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Virtualized LazyColumn with pre-cached lists and recycling content types
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // 1. "Use custom query" item if searching and no exact match
                        if (isSearching && filteredLanguages.none { it.equals(searchQuery.trim(), ignoreCase = true) }) {
                            item(key = "custom_search_option", contentType = "custom_search") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { handleSelectLanguage(searchQuery.trim()) }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Text(
                                        text = "Use \"${searchQuery.trim()}\"",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // 2. Section headers & items (browsing mode)
                        if (!isSearching) {
                            if (popularInList.isNotEmpty()) {
                                item(key = "header_popular", contentType = "header") {
                                    Text(
                                        text = stringResource(R.string.section_popular_languages),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp)
                                    )
                                }
                                items(
                                    items = popularInList,
                                    key = { "pop_$it" },
                                    contentType = { "language_item" }
                                ) { lang ->
                                    LanguageItemRow(
                                        language = lang,
                                        isSelected = lang.equals(targetLanguage, ignoreCase = true),
                                        onClick = { handleSelectLanguage(lang) }
                                    )
                                }
                            }

                            if (remainingInList.isNotEmpty()) {
                                item(key = "divider_all", contentType = "divider") {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                }
                                item(key = "header_all", contentType = "header") {
                                    Text(
                                        text = stringResource(R.string.section_all_languages),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp)
                                    )
                                }
                                items(
                                    items = remainingInList,
                                    key = { "all_$it" },
                                    contentType = { "language_item" }
                                ) { lang ->
                                    LanguageItemRow(
                                        language = lang,
                                        isSelected = lang.equals(targetLanguage, ignoreCase = true),
                                        onClick = { handleSelectLanguage(lang) }
                                    )
                                }
                            }
                        } else {
                            // Filtered search results
                            items(
                                items = filteredLanguages,
                                key = { it },
                                contentType = { "language_item" }
                            ) { lang ->
                                LanguageItemRow(
                                    language = lang,
                                    isSelected = lang.equals(targetLanguage, ignoreCase = true),
                                    onClick = { handleSelectLanguage(lang) }
                                )
                            }
                        }

                        // Custom Language button at bottom
                        item(key = "footer_custom", contentType = "footer") {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        customLanguageInput = if (targetLanguage !in availableLanguages) targetLanguage else ""
                                        showSelectionDialog = false
                                        searchQuery = ""
                                        showCustomDialog = true
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Text(
                                    text = stringResource(R.string.option_custom_value),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom Language Dialog
    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            shape = CardShape,
            title = { Text(stringResource(R.string.dialog_title_custom_language)) },
            text = {
                OutlinedTextField(
                    value = customLanguageInput,
                    onValueChange = { customLanguageInput = it },
                    label = { Text(stringResource(R.string.label_custom_language)) },
                    placeholder = { Text(stringResource(R.string.placeholder_custom_language)) },
                    singleLine = true,
                    shape = TextFieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customLanguageInput.isNotBlank()) {
                            onTargetLanguageChanged(customLanguageInput.trim())
                        }
                        showCustomDialog = false
                    },
                    shape = PillShape
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCustomDialog = false },
                    shape = PillShape
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

/**
 * Lightweight, flattened row item with zero unnecessary container wrappers for maximal Compose layout performance.
 */
@Composable
private fun LanguageItemRow(
    language: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = language,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
