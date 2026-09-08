package com.example.ui.subtitle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.subtitle.SubtitleCharacter
import com.example.ui.theme.TextFieldShape

@Composable
fun CharacterRowItem(
    character: SubtitleCharacter,
    onUpdate: (name: String, description: String) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = character.name,
            onValueChange = { onUpdate(it, character.description) },
            label = { Text(stringResource(R.string.hint_char_name)) },
            modifier = Modifier.weight(0.45f),
            singleLine = true,
        )

        OutlinedTextField(
            value = character.description,
            onValueChange = { onUpdate(character.name, it) },
            label = { Text(stringResource(R.string.hint_char_desc)) },
            modifier = Modifier.weight(0.55f),
            singleLine = true,
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.cd_delete_character),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}