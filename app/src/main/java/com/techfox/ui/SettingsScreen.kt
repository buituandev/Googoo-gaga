package com.techfox.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techfox.R
import com.techfox.ui.onboarding.OnboardingActivity
import com.techfox.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    state: UiState,
    modifier: Modifier = Modifier,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onCustomInstructionChanged: (String) -> Unit,
    onEnableInsightChanged: (Boolean) -> Unit = {},
    onClearHistory: () -> Unit,
    onDismissMessage: () -> Unit = {},
) {
    val context = LocalContext.current
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var customModelInput by remember { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onDismissMessage()
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Outlined text field labeled "Gemini API Key" with leading key icon and supporting text
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = onApiKeyChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("api_key_input"),
                label = { Text(stringResource(R.string.label_api_key)) },
                supportingText = {
                    Text(
                        text = stringResource(R.string.supporting_api_key),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { isApiKeyVisible = !isApiKeyVisible },
                        modifier = Modifier.testTag("toggle_api_key_visibility")
                    ) {
                        Icon(
                            imageVector = if (isApiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(
                                if (isApiKeyVisible) R.string.cd_hide_api_key else R.string.cd_show_api_key
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            val modelsList = state.availableModels.ifEmpty { PRESET_MODELS }

            // Outlined dropdown labeled "Gemini Model" with leading neurology icon and supporting text
            ExposedDropdownMenuBox(
                expanded = isModelDropdownExpanded,
                onExpandedChange = { isModelDropdownExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("model_dropdown")
            ) {
                OutlinedTextField(
                    value = state.model,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_model)) },
                    supportingText = {
                        Text(
                            text = stringResource(R.string.supporting_model),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (state.isLoadingModels) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModelDropdownExpanded)
                        }
                    }
                )

                ExposedDropdownMenu(
                    expanded = isModelDropdownExpanded,
                    onDismissRequest = { isModelDropdownExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    // Available models (fetched dynamically from API or preset fallback)
                    modelsList.forEach { modelName ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = modelName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (modelName == state.model) FontWeight.Bold else FontWeight.Normal,
                                    color = if (modelName == state.model) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onModelChanged(modelName)
                                isModelDropdownExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }

                    // If currently selected model is a custom one not in available models list
                    if (state.model !in modelsList && state.model.isNotBlank()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.custom_model_format, state.model),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                isModelDropdownExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Custom Model option
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.option_custom_value),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            customModelInput =
                                if (state.model !in modelsList) state.model else ""
                            isModelDropdownExpanded = false
                            showCustomModelDialog = true
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }

            // Custom Model Dialog
            if (showCustomModelDialog) {
                AlertDialog(
                    onDismissRequest = { showCustomModelDialog = false },
                    title = { Text(stringResource(R.string.dialog_title_custom_model)) },
                    text = {
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = { customModelInput = it },
                            label = { Text(stringResource(R.string.label_custom_model)) },
                            placeholder = { Text(stringResource(R.string.placeholder_custom_model)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (customModelInput.isNotBlank()) {
                                    onModelChanged(customModelInput.trim())
                                }
                                showCustomModelDialog = false
                            },
                            shape = PillShape
                        ) {
                            Text(stringResource(R.string.btn_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showCustomModelDialog = false },
                            shape = PillShape
                        ) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Outlined text field for Custom System Instructions to override hardcoded instructions
            OutlinedTextField(
                value = state.customInstruction,
                onValueChange = onCustomInstructionChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("custom_instruction_input"),
                label = { Text(stringResource(R.string.label_custom_instruction)) },
                placeholder = { Text(stringResource(R.string.placeholder_custom_instruction)) },
                supportingText = {
                    Text(
                        text = stringResource(R.string.supporting_custom_instruction),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                trailingIcon = {
                    if (state.customInstruction.isNotEmpty()) {
                        IconButton(
                            onClick = { onCustomInstructionChanged("") },
                            modifier = Modifier.testTag("clear_custom_instruction")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = stringResource(R.string.cd_clear_custom_instruction),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(16.dp))

            val preferenceItemsCount = if (state.history.isNotEmpty()) 3 else 2

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                // Item 0: Cultural Insights Toggle Item
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(
                        index = 0,
                        count = preferenceItemsCount
                    ),
                    onClick = { onEnableInsightChanged(!state.enableInsight) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.label_enable_insight),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.desc_enable_insight),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = state.enableInsight,
                            onCheckedChange = onEnableInsightChanged,
                            modifier = Modifier.testTag("enable_insight_switch")
                        )
                    }
                )

                // Item 1: Revisit Onboarding & Setup Guide Item
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(
                        index = 1,
                        count = preferenceItemsCount
                    ),
                    onClick = {
                        context.startActivity(OnboardingActivity.createIntent(context, fromSettings = true))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_guide_item"),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    content = {
                        Text(
                            text = stringResource(R.string.label_onboarding_guide),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.desc_onboarding_guide),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                            contentDescription = stringResource(R.string.btn_view_guide),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                // Item 2: History Management Item
                if (state.history.isNotEmpty()) {
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(
                            index = 2,
                            count = preferenceItemsCount
                        ),
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        content = {
                            Text(
                                text = stringResource(R.string.title_history),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.history_storage_desc, state.history.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            OutlinedButton(
                                onClick = onClearHistory,
                                shape = PillShape,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("clear_history_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_clear),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


