package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.PRESET_LANGUAGES
import com.example.ui.theme.CardShape
import com.example.ui.theme.PillShape
import com.example.ui.theme.TextFieldShape

/**
 * Reusable language selector dropdown with preset options and a custom language input dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdownSelector(
    targetLanguage: String,
    onTargetLanguageChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    availableLanguages: List<String> = PRESET_LANGUAGES
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customLanguageInput by remember { mutableStateOf("") }

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it },
        modifier = modifier
            .fillMaxWidth()
            .testTag("target_language_dropdown")
    ) {
        OutlinedTextField(
            value = targetLanguage,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
            label = { Text(stringResource(R.string.label_target_language)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
            }
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            // Preset languages
            availableLanguages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = language,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (language == targetLanguage) FontWeight.Bold else FontWeight.Normal,
                            color = if (language == targetLanguage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onTargetLanguageChanged(language)
                        isExpanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }

            // If currently selected language is a custom one not in presets
            if (targetLanguage !in availableLanguages && targetLanguage.isNotBlank()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.custom_language_format, targetLanguage),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        isExpanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Custom Language option
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
                    customLanguageInput = if (targetLanguage !in availableLanguages) targetLanguage else ""
                    isExpanded = false
                    showCustomDialog = true
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
            )
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
