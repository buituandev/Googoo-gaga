package com.techfox.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.techfox.R
import com.techfox.data.KeyTermInsight
import com.techfox.data.LanguageDetector
import com.techfox.data.TranslationEntity
import com.techfox.ui.components.ExpressiveIconButton
import com.techfox.ui.components.InsightBottomSheet
import com.techfox.ui.components.InteractiveTranslationText
import com.techfox.ui.components.KeyTermBottomSheet
import com.techfox.ui.components.LanguageDropdownSelector
import com.techfox.ui.components.copyToClipboard
import com.techfox.ui.components.rememberTtsController
import com.techfox.ui.components.shareText
import com.techfox.ui.theme.CardShape
import com.techfox.ui.theme.PillShape
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    state: UiState,
    onInputChanged: (String) -> Unit,
    onTargetLanguageChanged: (String) -> Unit,
    onTranslateClicked: () -> Unit,
    onClearClicked: () -> Unit,
    onHistoryItemSelected: (TranslationEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showNuanceBottomSheet by remember { mutableStateOf(false) }
    var selectedKeyTerm by remember { mutableStateOf<KeyTermInsight?>(null) }
    var showSameLanguageDialog by remember { mutableStateOf(false) }
    var isCheckingLanguage by remember { mutableStateOf(false) }
    val nuanceSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val keyTermSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    val ttsController = rememberTtsController()

    val handlePaste: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
        if (clipText.isNotBlank()) {
            onInputChanged(clipText)
        }
    }

    val handleTranslateAttempt: () -> Unit = {
        val input = state.inputText.trim()
        if (input.isNotBlank() && !state.isTranslating && !isCheckingLanguage) {
            isCheckingLanguage = true
            coroutineScope.launch {
                val detectedLang = LanguageDetector.identifyLanguage(input)
                isCheckingLanguage = false
                if (LanguageDetector.isSameLanguage(detectedLang, state.targetLanguage)) {
                    showSameLanguageDialog = true
                } else {
                    onTranslateClicked()
                }
            }
        } else if (!state.isTranslating && !isCheckingLanguage) {
            onTranslateClicked()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.title_gemini_translator),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                Image(
                    painter = painterResource(id = R.drawable.aicube),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .padding(start = 16.dp, end = 4.dp)
                        .size(46.dp),
                    contentScale = ContentScale.Fit
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.inputText,
            onValueChange = onInputChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("input_text_field"),
            label = { Text(stringResource(R.string.label_enter_text)) },
            placeholder = { Text(stringResource(R.string.placeholder_enter_text)) },
            minLines = 3,
            maxLines = 9,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Target language selector dropdown with custom dialog
        LanguageDropdownSelector(
            targetLanguage = state.targetLanguage,
            onTargetLanguageChanged = onTargetLanguageChanged,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        val hasInput = state.inputText.isNotEmpty()
        ButtonGroup(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            overflowIndicator = { menuState ->
                ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
            }
        ) {
            customItem(
                buttonGroupContent = {
                    OutlinedButton(
                        onClick = if (hasInput) onClearClicked else handlePaste,
                        shape = ButtonGroupDefaults.connectedLeadingButtonShapes().shape,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag(if (hasInput) "clear_text_button" else "paste_text_button")
                    ) {
                        Icon(
                            imageVector = if (hasInput) Icons.Rounded.Clear else Icons.Rounded.ContentPaste,
                            contentDescription = stringResource(
                                if (hasInput) R.string.cd_clear_text else R.string.cd_paste_text
                            ),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(if (hasInput) R.string.btn_clear else R.string.btn_paste),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                menuContent = {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (hasInput) R.string.btn_clear else R.string.btn_paste)) },
                        onClick = if (hasInput) onClearClicked else handlePaste
                    )
                }
            )
            customItem(
                buttonGroupContent = {
                    Button(
                        onClick = handleTranslateAttempt,
                        enabled = !state.isTranslating && !isCheckingLanguage && state.inputText.isNotBlank(),
                        modifier = Modifier
                            .weight(2f)
                            .height(56.dp)
                            .testTag("translate_button"),
                        shape = ButtonGroupDefaults.connectedTrailingButtonShapes().shape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        if (state.isTranslating || isCheckingLanguage) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.translating_status),
                                style = MaterialTheme.typography.labelLarge
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_translate_context),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                menuContent = {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.btn_translate_context)) },
                        onClick = handleTranslateAttempt
                    )
                }
            )
        }

        // Error message if any
        AnimatedVisibility(visible = state.errorMessage != null) {
            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Translation & Insight Result Card (or empty state)
        val current = state.currentTranslation
        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(min = if (current != null) 160.dp else 140.dp)
                .testTag("translation_result_card"),
            shape = CardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            if (current != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.card_title_result),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Action buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExpressiveIconButton(
                                onClick = {
                                    ttsController.toggle(
                                        current.directTranslation, state.targetLanguage
                                    )
                                },
                                icon = if (ttsController.isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = stringResource(
                                    if (ttsController.isSpeaking) R.string.cd_stop_listen_translation else R.string.cd_listen_translation
                                ),
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("listen_button")
                            )

                            ExpressiveIconButton(
                                onClick = {
                                    copyToClipboard(context, current.directTranslation)
                                },
                                icon = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy_translation),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("copy_button")
                            )

                            val shareContent = stringResource(
                                R.string.share_insights_format,
                                current.directTranslation,
                                current.culturalContext
                            )
                            ExpressiveIconButton(
                                onClick = {
                                    shareText(context, shareContent)
                                },
                                icon = Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.cd_share_translation),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("share_button")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Direct Translation Value with interactive clickable key term highlights
                    InteractiveTranslationText(
                        text = current.directTranslation,
                        keyTerms = current.keyTerms,
                        onTermClicked = { selectedKeyTerm = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("translation_text_value")
                    )

                    // Distinct button to view Insights in the Bottom Sheet
                    if (current.culturalContext.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            onClick = { showNuanceBottomSheet = true },
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_nuance_bottom_sheet_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Psychology,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.label_cultural_nuances),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.empty_translation_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Key Term Insight Bottom Sheet
        KeyTermBottomSheet(
            visible = selectedKeyTerm != null,
            term = selectedKeyTerm,
            targetLanguage = state.targetLanguage,
            sourceLanguage = "auto",
            onDismiss = { selectedKeyTerm = null },
            sheetState = keyTermSheetState
        )

        // Insight Modal Bottom Sheet
        InsightBottomSheet(
            visible = showNuanceBottomSheet && current != null,
            onDismiss = { showNuanceBottomSheet = false },
            insightText = current?.culturalContext ?: "",
            directTranslation = current?.directTranslation ?: "",
            sheetState = nuanceSheetState
        )

        // Same Language Confirmation Alert Dialog
        if (showSameLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showSameLanguageDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.dialog_same_language_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.dialog_same_language_msg, state.targetLanguage),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSameLanguageDialog = false
                            onTranslateClicked()
                        },
                        shape = PillShape
                    ) {
                        Text(stringResource(R.string.btn_translate_anyway))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showSameLanguageDialog = false },
                        shape = PillShape
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        // Recent translations quick suggestion chips if available
        if (state.history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.title_recent_translations),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.history.take(6)) { item ->
                        SuggestionChip(
                            onClick = { onHistoryItemSelected(item) },
                            label = {
                                Text(
                                    text = item.sourceText.take(24) + if (item.sourceText.length > 24) "…" else "",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = PillShape,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
