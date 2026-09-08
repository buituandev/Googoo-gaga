package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.KeyTermInsight
import com.example.data.TranslationEntity
import com.example.data.TranslationRepository
import com.example.ui.components.ExpressiveIconButton
import com.example.ui.components.InsightBottomSheet
import com.example.ui.components.InteractiveTranslationText
import com.example.ui.components.KeyTermBottomSheet
import com.example.ui.components.LanguageDropdownSelector
import com.example.ui.components.copyToClipboard
import com.example.ui.components.rememberTtsController
import com.example.ui.components.shareText
import com.example.ui.theme.CardShape
import com.example.ui.theme.DialogShape
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PillShape
import kotlinx.coroutines.launch

class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Extract selected text across PROCESS_TEXT, SEND, or ClipData intents
        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            ?: ""

        // Extra process text readonly flag (true on read-only views like X feeds / captions)
        val isReadOnly = if (intent.action == Intent.ACTION_SEND) {
            true
        } else {
            intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        }

        val repository = TranslationRepository(this)
        val initialTargetLang = repository.preferences.getTargetLanguage()

        setContent {
            MyApplicationTheme {
                ProcessTextBottomSheet(
                    sourceText = selectedText,
                    isReadOnly = isReadOnly,
                    initialTargetLanguage = initialTargetLang,
                    onDismiss = { finish() },
                    onReplaceText = { replacedText ->
                        val resultIntent = Intent().apply {
                            putExtra(Intent.EXTRA_PROCESS_TEXT, replacedText)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                    repository = repository
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessTextBottomSheet(
    sourceText: String,
    isReadOnly: Boolean,
    initialTargetLanguage: String,
    onDismiss: () -> Unit,
    onReplaceText: (String) -> Unit,
    repository: TranslationRepository
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val scope = rememberCoroutineScope()

    var targetLanguage by remember { mutableStateOf(initialTargetLanguage) }
    var isTranslating by remember { mutableStateOf(true) }
    var translationResult by remember { mutableStateOf<TranslationEntity?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showNuanceSheet by remember { mutableStateOf(false) }
    var selectedKeyTerm by remember { mutableStateOf<KeyTermInsight?>(null) }
    val nuanceSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val keyTermSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    val ttsController = rememberTtsController()

    fun doTranslate(lang: String) {
        if (sourceText.isBlank()) {
            errorMessage = "No text provided to translate"
            isTranslating = false
            return
        }

        isTranslating = true
        errorMessage = null
        scope.launch {
            val result = repository.translateAndSave(sourceText, lang)
            result.onSuccess { entity ->
                translationResult = entity
                isTranslating = false
            }.onFailure { error ->
                errorMessage = error.localizedMessage ?: "Translation failed"
                isTranslating = false
            }
        }
    }

    LaunchedEffect(sourceText, targetLanguage) {
        doTranslate(targetLanguage)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = DialogShape,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("process_text_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Translation & Insight",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                ExpressiveIconButton(
                    onClick = onDismiss,
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close",
                    size = 36.dp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Source Text preview card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Selected Text",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = sourceText.ifBlank { "(Empty text)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Target language picker (reused)
            LanguageDropdownSelector(
                targetLanguage = targetLanguage,
                onTargetLanguageChanged = { targetLanguage = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Loading state or Result Card
            if (isTranslating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularWavyProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.translating_status),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (translationResult != null) {
                val res = translationResult!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.card_title_result),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            // Action icon buttons (TTS pronunciation, copy)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ExpressiveIconButton(
                                    onClick = { ttsController.toggle(res.directTranslation, targetLanguage) },
                                    icon = if (ttsController.isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = stringResource(
                                        if (ttsController.isSpeaking) R.string.cd_stop_listen_translation else R.string.cd_listen_translation
                                    ),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    size = 36.dp
                                )

                                ExpressiveIconButton(
                                    onClick = {
                                        copyToClipboard(context, res.directTranslation)
                                    },
                                    icon = Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_copy_translation),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    size = 36.dp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Translation value displayed with interactive clickable key term highlights
                        InteractiveTranslationText(
                            text = res.directTranslation,
                            keyTerms = res.keyTerms,
                            onTermClicked = { selectedKeyTerm = it }
                        )

                        // Button to view Insight in bottom sheet
                        if (res.culturalContext.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                onClick = { showNuanceSheet = true },
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("process_text_nuance_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Psychology,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.label_cultural_nuances),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons based on Read-Only vs Editable state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, res.directTranslation)
                        },
                        modifier = Modifier.weight(1f),
                        shape = PillShape
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_copy))
                    }

                    if (isReadOnly) {
                        // Read-only source (e.g. X post / tweet caption) -> Offer Share
                        val shareContent = stringResource(
                            R.string.share_insights_format,
                            res.directTranslation,
                            res.culturalContext
                        )
                        val shareTitle = stringResource(R.string.cd_share_translation)
                        Button(
                            onClick = {
                                shareText(
                                    context,
                                    shareContent,
                                    shareTitle
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_share))
                        }
                    } else {
                        // Editable source (e.g. typing in text box) -> Offer Replace
                        Button(
                            onClick = { onReplaceText(res.directTranslation) },
                            modifier = Modifier.weight(1f),
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_replace))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Key Term Insight Bottom Sheet
        KeyTermBottomSheet(
            visible = selectedKeyTerm != null,
            term = selectedKeyTerm,
            targetLanguage = targetLanguage,
            sourceLanguage = "auto",
            onDismiss = { selectedKeyTerm = null },
            sheetState = keyTermSheetState
        )

        // Insight Modal Bottom Sheet (reused)
        InsightBottomSheet(
            visible = showNuanceSheet && translationResult != null,
            onDismiss = { showNuanceSheet = false },
            insightText = translationResult?.culturalContext ?: "",
            directTranslation = translationResult?.directTranslation ?: "",
            sheetState = nuanceSheetState
        )
    }
}
