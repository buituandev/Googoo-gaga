package com.techfox.ui.subtitle

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techfox.R
import com.techfox.data.subtitle.InputFormat
import com.techfox.data.subtitle.SubtitleCharacter
import com.techfox.data.subtitle.SubtitlePreset
import com.techfox.ui.components.ExpressiveButton
import com.techfox.ui.components.ExpressiveIconButton
import com.techfox.ui.components.LanguageDropdownSelector
import com.techfox.ui.components.copyToClipboard
import com.techfox.ui.theme.CardShape
import com.techfox.ui.theme.PillShape
import com.techfox.ui.theme.TextFieldShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleScreen(
    state: SubtitleUiState,
    modifier: Modifier = Modifier,
    onInputChanged: (String) -> Unit,
    onFileLoaded: (String, String) -> Unit,
    onTargetLanguageChanged: (String) -> Unit,
    onContextChanged: (String) -> Unit,
    onPresetSelected: (SubtitlePreset) -> Unit = {},
    onCustomPromptChanged: (String) -> Unit = {},
    onAddCharacter: () -> Unit,
    onUpdateCharacter: (String, String, String) -> Unit,
    onRemoveCharacter: (String) -> Unit,
    onTranslateClicked: () -> Unit,
    onResumeClicked: () -> Unit,
    onDiscardResume: () -> Unit,
    onCancelClicked: () -> Unit,
    onClearClicked: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // File Open Launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = queryFileName(context, uri) ?: "subtitle"
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    onFileLoaded(fileName, content)
                }
            } catch (e: Exception) {
                // Ignore or handle read error
            }
        }
    }

    // Export File Launcher
    var contentToExport by remember { mutableStateOf("") }
    var showPreviewSheet by remember { mutableStateOf(false) }
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null && contentToExport.isNotBlank()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.bufferedWriter().use { it.write(contentToExport) }
                }
            } catch (e: Exception) {
                // File export exception
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("subtitle_screen")
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.title_subtitles),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Smart Pickup: Resumable Checkpoint Card
            AnimatedVisibility(
                visible = state.resumableJob != null && !state.isTranslating,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                state.resumableJob?.let { job ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Restore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.resumable_job_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.resumable_job_desc,
                                    job.fileName,
                                    job.completedChunks,
                                    job.totalChunks
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onDiscardResume,
                                    shape = PillShape
                                ) {
                                    Text(stringResource(R.string.btn_discard))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onResumeClicked,
                                    shape = PillShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_resume_translation))
                                }
                            }
                        }
                    }
                }
            }

            // 1. Format Badge and Action Bar (Directly on screen, not inside a Card)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Format Badge
                Surface(
                    shape = PillShape,
                    color = if (state.detectedFormat.isSubtitle) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (state.detectedFormat) {
                                InputFormat.SUBTITLE_SRT, InputFormat.SUBTITLE_VTT -> Icons.Rounded.Subtitles
                                InputFormat.JSON -> Icons.Rounded.Code
                                InputFormat.LRC -> Icons.Rounded.MusicNote
                                else -> Icons.Rounded.Description
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (state.detectedFormat.isSubtitle) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val formatName = stringResource(state.detectedFormat.nameRes)
                        Text(
                            text = state.fileName?.let { "$it ($formatName)" } ?: formatName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Actions: Open File, Paste, Clear
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveIconButton(
                        onClick = {
                            openDocumentLauncher.launch(
                                arrayOf(
                                    "application/x-subrip",
                                    "text/vtt",
                                    "application/json",
                                    "text/markdown",
                                    "text/plain"
                                )
                            )
                        },
                        icon = Icons.Rounded.FolderOpen,
                        contentDescription = stringResource(R.string.btn_pick_file),
                    )

                    ExpressiveIconButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                            if (clipText.isNotBlank()) {
                                onInputChanged(clipText)
                            }
                        },
                        icon = Icons.Rounded.ContentPaste,
                        contentDescription = stringResource(R.string.cd_paste_text),
                    )

                    if (state.inputText.isNotEmpty()) {
                        ExpressiveIconButton(
                            onClick = onClearClicked,
                            icon = Icons.Rounded.Clear,
                            contentDescription = stringResource(R.string.cd_clear_text),
                        )
                    }
                }
            }

            // 2. Main Text Input
            OutlinedTextField(
                value = state.inputText,
                onValueChange = onInputChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 240.dp),
                label = {
                    Text(
                        if (state.detectedFormat.isSubtitle) stringResource(R.string.label_subtitle_content)
                        else stringResource(R.string.label_document_content)
                    )
                },
                placeholder = {
                    Text(
                        text = if (state.detectedFormat.isSubtitle) {
                            stringResource(R.string.placeholder_subtitle_content)
                        } else {
                            stringResource(R.string.placeholder_document_content)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                maxLines = 15
            )

            // Token Guard Notice (Directly on screen if active)
            if (state.detectedFormat.isSubtitle && state.noiseCuesPreserved > 0) {
                Surface(
                    shape = TextFieldShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                R.string.token_guard_active,
                                state.noiseCuesPreserved
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Target Language Selector (Directly on screen, not inside a Card)
            LanguageDropdownSelector(
                targetLanguage = state.targetLanguage,
                onTargetLanguageChanged = onTargetLanguageChanged
            )

            // 4. Tone Preset Selector (Directly on screen, not inside a Card)
            SubtitlePresetDropdownSelector(
                selectedPreset = state.selectedPreset,
                onPresetSelected = onPresetSelected
            )

            // Custom Directives Input: Shown directly on screen when CUSTOM preset is selected
            AnimatedVisibility(
                visible = state.selectedPreset.isCustom,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.custom_prompt_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = state.customPrompt,
                        onValueChange = onCustomPromptChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.custom_prompt_placeholder),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                    )
                }
            }

            // 5. Storyline & Context Input (Directly on screen, not inside a Card)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.label_storyline_context),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = state.contextDescription,
                    onValueChange = onContextChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 160.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.placeholder_storyline_context),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                )
            }

            // 5. Characters Section: ONLY shown when detected format is Subtitle! (Directly on screen, not inside a Card)
            AnimatedVisibility(
                visible = state.detectedFormat.isSubtitle,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val canAddMore = state.characters.size < SubtitleCharacter.MAX_CHARACTERS_COUNT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.label_characters),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = PillShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = "${state.characters.size}/${SubtitleCharacter.MAX_CHARACTERS_COUNT}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 2.dp
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.desc_characters),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        ExpressiveIconButton(
                            onClick = onAddCharacter,
                            enabled = canAddMore,
                            icon = Icons.Rounded.PersonAdd,
                            contentDescription = stringResource(R.string.btn_add_character),
                        )
                    }

                    if (state.characters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        state.characters.forEachIndexed { index, char ->
                            CharacterRowItem(
                                character = char,
                                onUpdate = { name, desc ->
                                    onUpdateCharacter(char.id, name, desc)
                                },
                                onRemove = { onRemoveCharacter(char.id) }
                            )
                            if (index < state.characters.size - 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }

            // 6. Primary Translate Button
            ExpressiveButton(
                onClick = onTranslateClicked,
                enabled = !state.isTranslating && state.inputText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_translate_subtitle"),
                text = if (state.detectedFormat.isSubtitle) {
                    stringResource(R.string.btn_translate_subtitle)
                } else {
                    stringResource(R.string.btn_translate_formatted)
                }
            )

            // 7. Progress Card (Active during translation)
            AnimatedVisibility(
                visible = state.isTranslating,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(52.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        if (state.detectedFormat.isSubtitle) {
                            Text(
                                text = stringResource(
                                    R.string.progress_translating_chunk,
                                    state.currentChunk,
                                    state.totalChunks
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.progress_cues_completed,
                                    state.completedCues,
                                    state.totalCues
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (state.totalChunks > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        (state.currentChunk.toFloat() / state.totalChunks.coerceAtLeast(
                                            1
                                        )).coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(PillShape)
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.progress_translating_document),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onCancelClicked,
                            shape = PillShape
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_stop))
                        }
                    }
                }
            }

            // 8. Completion & Export Presentation
            AnimatedVisibility(
                visible = state.isCompleted && !state.isTranslating,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.detectedFormat.isSubtitle) {
                                    stringResource(R.string.title_subtitle_translation_complete)
                                } else {
                                    stringResource(R.string.title_translation_complete)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stats summary for subtitles & documents (no text inputs in card)
                        if (state.detectedFormat.isSubtitle) {
                            Text(
                                text = stringResource(
                                    R.string.subtitle_stats_summary,
                                    state.totalCues,
                                    state.completedCues,
                                    state.noiseCuesPreserved
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.document_stats_summary,
                                    state.translatedOutputText.length,
                                    stringResource(state.detectedFormat.nameRes)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // Preview & Export Actions: Full-width Preview Button, followed by Export & Copy Row
                        Button(
                            onClick = { showPreviewSheet = true },
                            shape = PillShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_preview_translation")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_preview_translation),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    contentToExport = state.translatedOutputText
                                    val suggestedName =
                                        "translated_${state.fileName ?: "output.${state.detectedFormat.extension}"}"
                                    exportFileLauncher.launch(suggestedName)
                                },
                                shape = PillShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_export_file),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, state.translatedOutputText)
                                },
                                shape = PillShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_copy_output),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Preview Bottom Sheet
        SubtitlePreviewBottomSheet(
            visible = showPreviewSheet,
            onDismiss = { showPreviewSheet = false },
            format = state.detectedFormat,
            cues = state.translatedCues,
            rawText = state.translatedOutputText,
            onExportFile = {
                contentToExport = state.translatedOutputText
                val suggestedName =
                    "translated_${state.fileName ?: "output.${state.detectedFormat.extension}"}"
                exportFileLauncher.launch(suggestedName)
            }
        )
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex)
                }
            }
        }
    }
    return uri.path?.substringAfterLast('/')
}

